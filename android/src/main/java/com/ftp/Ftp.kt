package com.ftp


import android.annotation.SuppressLint
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

val myPluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)



class RNFtpClientModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(
    reactContext
  ) {
  private var ip_address: String? = null
  private var port = 0
  private var username: String? = null
  private var password: String? = null
  private val uploadingTasks = HashMap<String, Job>()
  private val downloadingTasks = HashMap<String, Job>()

  @ReactMethod
  fun setup(ip_address: String?, port: Int, username: String?, password: String?) {
    this.ip_address = ip_address
    this.port = port
    this.username = username
    this.password = password
  }


  @Throws(IOException::class)
  private fun login(client: FTPClient) {
    if(this.port.equals(0)){
      client.connect(this.ip_address)
    }
    else{
      client.connect(this.ip_address, this.port)
    }
    client.enterLocalPassiveMode()
    client.login(this.username, this.password)
  }

  private fun logout(client: FTPClient) {
    try {
      client.logout()
    } catch (e: IOException) {
      Log.d(TAG, "logout error", e)
    }
    try {
      if (client.isConnected()) {
        client.disconnect()
      }
    } catch (e: IOException) {
      Log.d(TAG, "logout disconnect error", e)
    }
  }

  private fun getStringByType(type: Int): String {
    return when (type) {
      FTPFile.DIRECTORY_TYPE -> "dir"
      FTPFile.FILE_TYPE -> "file"
      FTPFile.SYMBOLIC_LINK_TYPE -> "link"
      FTPFile.UNKNOWN_TYPE -> "unknown"
      else -> "unknown"
    }
  }

  private fun ISO8601StringFromCalender(calendar: Calendar): String {
    val date = calendar.time
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    sdf.timeZone = TimeZone.getTimeZone("CET")
    return sdf.format(date)
  }

  private fun launchCoroutine(onError:(message:String)->Unit,block: suspend CoroutineScope.() -> Unit): Job {
    val handler = CoroutineExceptionHandler { _, exception ->
      onError(exception.message.toString())
      println("CoroutineExceptionHandler got $exception")
    }
  return  myPluginScope.launch(handler) {
     block()
    }
  }

  @ReactMethod
  fun list(path: String?, promise: Promise) {
    launchCoroutine(onError = { promise.reject(RNFTPCLIENT_ERROR_CODE_LIST, it) }) {
      val files: Array<FTPFile?>
      val client = FTPClient()
      try {
        login(client)
        files = client.listFiles(path)
        val result = Arguments.createArray()
        for (file in files) {
          val tmp = Arguments.createMap()
          tmp.putString("name", file?.name)
          file?.size?.let { tmp.putInt("size", it.toInt()) }
          tmp.putString("timestamp", ISO8601StringFromCalender(file!!.timestamp))
          tmp.putString("type", getStringByType(file.type))
          result.pushMap(tmp)
        }
        promise.resolve(result)
      } catch (e: Exception) {
        promise.reject(RNFTPCLIENT_ERROR_CODE_LIST, e.message)
      } finally {
        logout(client)
      }
    }
  }

  //remove file or dir
  @ReactMethod
  fun remove(path: String, promise: Promise) {
    launchCoroutine(onError = { promise.reject(RNFTPCLIENT_ERROR_CODE_REMOVE, it) }) {
      val client: FTPClient = FTPClient()
      try {
        login(client)
        if (path.endsWith(File.separator)) {
          client.removeDirectory(path)
        } else {
          client.deleteFile(path)
        }
        promise.resolve(true)
      } catch (e: IOException) {
        promise.reject("ERROR", e.message)
      } finally {
        logout(client)
      }
    }
  }

  private fun makeToken(path: String, remoteDestinationDir: String): String {
    return String.format("%s=>%s", path, remoteDestinationDir)
  }

  private fun makeDownloadToken(path: String, remoteDestinationDir: String): String {
    return String.format("%s<=%s", path, remoteDestinationDir)
  }

  private fun sendEvent(
    reactContext: ReactContext,
    eventName: String,
    params: WritableMap?
  ) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }

  private fun sendProgressEventToToken(token: String, percentage: Int) {
    val params = Arguments.createMap()
    params.putString("token", token)
    params.putInt("percentage", percentage)

    Log.d(TAG, "send progress $percentage to:$token")
    this.sendEvent(this.reactContext, RNFTPCLIENT_PROGRESS_EVENT_NAME, params)
  }

  override fun getConstants(): Map<String, String> {
    val constants: Map<String, String> = mapOf(pair=Pair(ERROR_MESSAGE_CANCELLED,ERROR_MESSAGE_CANCELLED) )
    return constants
  }

  @ReactMethod
  fun uploadFile(path: String, remoteDestinationPath: String, promise: Promise) {
    val token = makeToken(path, remoteDestinationPath)
    if (uploadingTasks.containsKey(token)) {
      promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD, "same upload is runing")
      return
    }
    if (uploadingTasks.size >= MAX_UPLOAD_COUNT) {
      promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD, "has reach max uploading tasks")
      return
    }
    val job =
      launchCoroutine(onError = { promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD, it) }) {
        val client = FTPClient()
        try {
          login(client)
          client.setFileType(FTP.BINARY_FILE_TYPE)
          val localFile = File(path)
          val totalBytes = localFile.length()
          var finishBytes: Long = 0

          val inputStream: InputStream = FileInputStream(localFile)

          Log.d(TAG, "Start uploading file")

          val outputStream: OutputStream = client.storeFileStream(remoteDestinationPath)
          val bytesIn = ByteArray(4096)
          var read = 0

          sendProgressEventToToken(token, 0)
          Log.d(TAG, "Resolve token:$token")
          var lastPercentage = 0
          while ((inputStream.read(bytesIn)
              .also { read = it }) != -1 && isActive
          ) {
            outputStream.write(bytesIn, 0, read)
            finishBytes += read.toLong()
            val newPercentage = (finishBytes * 100 / totalBytes).toInt()
            if (newPercentage > lastPercentage) {
              sendProgressEventToToken(token, newPercentage)
              lastPercentage = newPercentage
            }
          }
          inputStream.close()
          outputStream.close()
          Log.d(TAG, "Finish uploading")

          //if not interrupted
          if (isActive) {
            val done: Boolean = client.completePendingCommand()

            if (done) {
              promise.resolve(true)
            } else {
              promise.reject(
                RNFTPCLIENT_ERROR_CODE_UPLOAD,
                localFile.name + " is not uploaded successfully."
              )
              client.deleteFile(remoteDestinationPath)
            }
          } else {
            //interupted, the file will deleted by cancel update operation
            promise.reject(
              RNFTPCLIENT_ERROR_CODE_UPLOAD,
              ERROR_MESSAGE_CANCELLED
            )
          }
        } catch (e: IOException) {
          promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD, e.message)
        } finally {
          uploadingTasks.remove(token)
          logout(client)
        }
      }
    uploadingTasks[token] = job
  }

  @ReactMethod
   fun cancelUploadFile(token: String, promise: Promise) {


    val upload = uploadingTasks[token]

    if (upload == null) {
      promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD, "token is wrong")
      return
    }
    launchCoroutine(onError = { promise.reject(RNFTPCLIENT_ERROR_CODE_CANCELUPLOAD, it) }) {
      upload.cancel()
      val client = FTPClient()
      try {
        upload.join()
        login(client)
        val remoteFile = token.split("=>".toRegex()).dropLastWhile { it.isEmpty() }
          .toTypedArray()[1]
        client.deleteFile(remoteFile)
      } catch (e: Exception) {
        Log.d(TAG, "cancel upload error", e)
      } finally {
        logout(client)
      }
      uploadingTasks.remove(token)
      promise.resolve(true)
    }
  }

  private fun getLocalFilePath(path: String, remotePath: String): String {
    if (path.endsWith("/")) {
      val index = remotePath.lastIndexOf("/")
      return path + remotePath.substring(index + 1)
    } else {
      return path
    }
  }

  @Throws(Exception::class)
  private fun getRemoteSize(client: FTPClient, remoteFilePath: String): Long {
    client.sendCommand("SIZE", remoteFilePath)
    val reply: Array<String> = client.getReplyStrings()
    val response = reply[0].split(" ".toRegex()).dropLastWhile { it.isEmpty() }
      .toTypedArray()
    if (client.getReplyCode() !== 213) {
      throw Exception(
        java.lang.String.format(
          "ftp client size cmd response %d",
          client.getReplyCode()
        )
      )
    }
    return response[1].toLong()
  }



  @ReactMethod
  fun downloadFile(path: String, remoteDestinationPath: String, promise: Promise) {
    val token = makeDownloadToken(path, remoteDestinationPath)
    if (downloadingTasks.containsKey(token)) {
      promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD, "same downloading task is runing")
      return
    }
    if (downloadingTasks.size >= MAX_DOWNLOAD_COUNT) {
      promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD, "has reach max downloading tasks")
      return
    }
    if (remoteDestinationPath.endsWith("/")) {
      promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD, "remote path can not be a dir")
      return
    }

    val t =
      launchCoroutine(onError = { promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD, it) }) {
        val client = FTPClient()
        try {
          login(client)
          client.setFileType(FTP.BINARY_FILE_TYPE)

          val totalBytes = getRemoteSize(client, remoteDestinationPath)
          val downloadFile = File(getLocalFilePath(path, remoteDestinationPath))
          if (downloadFile.exists()) {
            throw Error(
              String.format(
                "local file exist",
                downloadFile.absolutePath
              )
            )
          }
          val parentDir = downloadFile.parentFile
          if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
          }
          downloadFile.createNewFile()
          var finishBytes: Long = 0

          Log.d(TAG, "Start downloading file")

          val outputStream: OutputStream =
            BufferedOutputStream(FileOutputStream(downloadFile))
          val inputStream: InputStream = client.retrieveFileStream(remoteDestinationPath)
          val bytesIn = ByteArray(4096)
          var read = 0

          sendProgressEventToToken(token, 0)
          Log.d(TAG, "Resolve token:$token")
          var lastPercentage = 0

          while ((inputStream.read(bytesIn)
              .also { read = it }) != -1 && isActive
          ) {
            outputStream.write(bytesIn, 0, read)
            finishBytes += read.toLong()
            val newPercentage = (finishBytes * 100 / totalBytes).toInt()
            if (newPercentage > lastPercentage) {
              sendProgressEventToToken(token, newPercentage)
              lastPercentage = newPercentage
            }
          }
          inputStream.close()
          outputStream.close()
          Log.d(TAG, "Finish uploading")

          //if not interrupted
          if (isActive) {
            val done: Boolean = client.completePendingCommand()

            if (done) {
              promise.resolve(true)
            } else {
              promise.reject(
                RNFTPCLIENT_ERROR_CODE_DOWNLOAD,
                downloadFile.name + " is not download successfully."
              )
              downloadFile.delete()
            }
          } else {
            //interupted, the file will deleted by cancel download operation
            promise.reject(
              RNFTPCLIENT_ERROR_CODE_DOWNLOAD,
              ERROR_MESSAGE_CANCELLED
            )
            downloadFile.delete()
          }
        } catch (e: Exception) {
          promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD, e.message)
        } finally {
          downloadingTasks.remove(token)
          logout(client)
        }
      }
    downloadingTasks[token] = t
  }

  @ReactMethod
   fun cancelDownloadFile(token: String, promise: Promise) {
    val download = downloadingTasks[token]

    if (download == null) {
      promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD, "token is wrong")
      return
    }
    launchCoroutine(onError = { promise.reject(ERROR_MESSAGE_CANCELLED, it) }) {
      download.cancel()
      val client: FTPClient = FTPClient()
      try {
        download.join()
      } catch (e: Exception) {
        Log.d(TAG, "cancel download error", e)
      }
      downloadingTasks.remove(token)
      promise.resolve(true)
    }
  }

  override fun getName(): String {
    return "RNFtpClient"
  }

  companion object {
    private const val TAG = "RNFtpClient"
    private const val MAX_UPLOAD_COUNT = 10

    private const val MAX_DOWNLOAD_COUNT = 10

    private const val RNFTPCLIENT_PROGRESS_EVENT_NAME = "Progress"

    private const val RNFTPCLIENT_ERROR_CODE_LOGIN = "RNFTPCLIENT_ERROR_CODE_LOGIN"
    private const val RNFTPCLIENT_ERROR_CODE_LIST = "RNFTPCLIENT_ERROR_CODE_LIST"
    private const val RNFTPCLIENT_ERROR_CODE_UPLOAD = "RNFTPCLIENT_ERROR_CODE_UPLOAD"
    private const val RNFTPCLIENT_ERROR_CODE_CANCELUPLOAD = "RNFTPCLIENT_ERROR_CODE_CANCELUPLOAD"
    private const val RNFTPCLIENT_ERROR_CODE_REMOVE = "RNFTPCLIENT_ERROR_CODE_REMOVE"
    private const val RNFTPCLIENT_ERROR_CODE_LOGOUT = "RNFTPCLIENT_ERROR_CODE_LOGOUT"
    private const val RNFTPCLIENT_ERROR_CODE_DOWNLOAD = "RNFTPCLIENT_ERROR_CODE_DOWNLOAD"

    private const val ERROR_MESSAGE_CANCELLED = "ERROR_MESSAGE_CANCELLED"
  }
}
