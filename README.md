
# rn-ftp-client
React Native New Architecture - FTP client implementation for React Native (Android only), Coroutine inside.
+ 🐎 New Architecture
+ ⚡ Using light-weight thread
+ 🏎 Kotlin implement

**_NOTE:_**  Android only

## Installation

### Adding the package

#### npm

```bash
$ npm install rn-ftp-client
```

#### yarn

```bash
$ yarn add rn-ftp-client
```

### Example

```typescript
import { setup, list, uploadFile, downloadFile } from 'rn-ftp-client';

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
      .catch((e) => console.log('error', e));
  };

  const _downloadFile = async () => {
    downloadFile('/sdcard/Download/testpic4.png', './testpic.png').catch(
      (e) => console.log('error', e)
    );
  };
```


## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
