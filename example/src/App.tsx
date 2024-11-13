import { useState, useEffect } from 'react';
import { StyleSheet, View, Text, Button } from 'react-native';
import { setup, list, uploadFile, downloadFile } from 'rn-ftp-client';
import { TListResult } from '../../src/type';

// const ip = 'ftp.dlptest.com';

// const user = 'dlpuser';
// const password = 'rNrKYTX9g7z3RgJRmxWuGHbeu';

const ip = 'eu-central-1.sftpcloud.io';
const user = '14e3ecc233444834aa1f475da6f5381c';
const password = 'UaPItfNCsFl0WllXZvp1Y1CbL09Jueye';

const fileNameUpload = 'testpic.png';
const fileNameDownload = 'testpic2.png';

export default function App() {
  const [result, setResult] = useState([]);

  const _setUp = async () => {
    setup(ip, 21, user, password).then((result) => {
      console.log(result);
    });
  };

  const _listFile = async () => {
    const files = await list('.');
    console.log('files' + files);
    setResult(files);
    return files;
  };

  const _uploadFile = async () => {
    uploadFile('/sdcard/Download/testpic.png', './testpic.png')
      .then((value) => console.log(value))
      .catch((e) => console.log('erro123', e));
  };

  const _downloadFile = async () => {
    downloadFile('/sdcard/Download/testpic4.png', './testpic.png').catch(
      (e) => console.log('error123', e)
    );
  };

  return (
    <View style={styles.container}>
      <Button title="setup" onPress={_setUp} />
      <Button title="list file" onPress={_listFile} />
      {result.map((item) => {
        return <Text>{item.name}</Text>;
      })}
      <Button title="upload" onPress={_uploadFile} />
      <Button title="download" onPress={_downloadFile} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
});
