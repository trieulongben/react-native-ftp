package com.ftp

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.Promise

class FtpModule internal constructor(context: ReactApplicationContext) :
  FtpSpec(context) {

  override fun getName(): String {
    return NAME
  }

  override fun setup(
    ipAddress: String?,
    port: Double,
    username: String?,
    password: String?,
    promise: Promise?
  ) {
    val portInt=port.toInt()
    if(promise==null) return
    ftpModule.setup(ipAddress,portInt,username,password)
  }

  private val ftpModule=RNFtpClientModule(reactContext = context)

  override fun list(path: String?, promise: Promise?) {
    if(promise==null || path==null) return
    return ftpModule.list(path,promise)
  }

  override fun remove(path: String?, promise: Promise?) {
    if(promise==null || path==null) return
    return ftpModule.remove(path,promise)
  }

  override fun uploadFile(path: String?,remoteDestinationPath: String?, promise: Promise?) {
    if(promise==null || path==null || remoteDestinationPath==null) return
    return ftpModule.uploadFile(path,remoteDestinationPath,promise)
  }

  override fun cancelUploadFile(path: String?, promise: Promise?) {
    if(promise==null || path==null) return
    return ftpModule.cancelUploadFile(path,promise)  }

  override fun downloadFile(path: String?, remoteDestinationPath: String?, promise: Promise?) {
    if(promise==null || path==null || remoteDestinationPath==null) return
    return ftpModule.downloadFile (path,remoteDestinationPath,promise)
  }

  override fun cancelDownloadFile(token: String?, promise: Promise?) {
    if(promise==null || token==null) return
    return ftpModule.cancelDownloadFile(token,promise)
  }


  companion object {
    const val NAME = "Ftp"
  }
}
