
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNFtpSpec.h"

@interface Ftp : NSObject <NativeFtpSpec>
#else
#import <React/RCTBridgeModule.h>

@interface Ftp : NSObject <RCTBridgeModule>
#endif

@end
