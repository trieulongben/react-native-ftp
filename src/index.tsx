import { NativeModules, Platform } from 'react-native';
import type { TListResult } from './type';

const LINKING_ERROR =
  `The package 'react-native-ftp' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

// @ts-expect-error
const isTurboModuleEnabled = global.__turboModuleProxy != null;

const FtpModule = isTurboModuleEnabled
  ? require('./NativeFtp').default
  : NativeModules.Ftp;

const Ftp = FtpModule
  ? FtpModule
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export function setup(
  ipAddress: string,
  port: number,
  username: string,
  password: string
): Promise<void> {
  return Ftp.setup(ipAddress, port, username, password);
}

export function list(path: string): Promise<TListResult> {
  return Ftp.list(path);
}

export function remove(path: string): Promise<boolean> {
  return Ftp.remove(path);
}

export function uploadFile(
  path: string,
  remoteDestinationPath: string
): Promise<boolean> {
  return Ftp.uploadFile(path, remoteDestinationPath);
}

export function cancelUploadFile(path: string): Promise<boolean> {
  return Ftp.cancelUploadFile(path);
}

export function downloadFile(
  path: string,
  remoteDestinationPath: string
): Promise<boolean> {
  return Ftp.downloadFile(path, remoteDestinationPath);
}

export function cancelDownloadFile(token: string): Promise<boolean> {
  return Ftp.cancelDownloadFile(token);
}
