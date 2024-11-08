import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { TListResult } from './type';

export interface Spec extends TurboModule {
  setup(
    ipAddress: string,
    port: number,
    username: string,
    password: string
  ): Promise<void>;
  list(path: string): Promise<Array<TListResult>>;
  remove(path: string): Promise<boolean>;
  uploadFile(path: string, remoteDestinationPath: string): Promise<boolean>;
  cancelUploadFile(path: string): Promise<boolean>;
  downloadFile(path: string, remoteDestinationPath: string): Promise<boolean>;
  cancelDownloadFile(token: string): Promise<boolean>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Ftp');
