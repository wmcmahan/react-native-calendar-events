#import "RNCalendarEvents.h"

#if __has_include("RNCalendarEvents-Swift.h")
#import "RNCalendarEvents-Swift.h"
#else
#import <RNCalendarEvents/RNCalendarEvents-Swift.h>
#endif

/// Thin adapter between the codegen'd Turbo Module spec and the Swift core.
/// No calendar logic lives here: every method forwards to `RNCalendarEventsStore`.
@implementation RNCalendarEvents {
  RNCalendarEventsStore *_store;
}

- (instancetype)init
{
  if (self = [super init]) {
    _store = [RNCalendarEventsStore new];
  }
  return self;
}

+ (NSString *)moduleName
{
  return @"RNCalendarEvents";
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeCalendarEventsSpecJSI>(params);
}

#pragma mark - Permissions

- (void)checkPermissions:(BOOL)readOnly
                 resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject
{
  // readOnly is an Android concept; iOS ignores it.
  resolve([_store checkPermissions]);
}

- (void)requestPermissions:(BOOL)readOnly
                   resolve:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject
{
  [_store requestPermissions:resolve reject:reject];
}

#pragma mark - Calendars

- (void)findCalendars:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  [_store findCalendars:resolve reject:reject];
}

- (void)saveCalendar:(NSDictionary *)options
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject
{
  [_store saveCalendar:options resolve:resolve reject:reject];
}

- (void)removeCalendar:(NSString *)id
               resolve:(RCTPromiseResolveBlock)resolve
                reject:(RCTPromiseRejectBlock)reject
{
  [_store removeCalendar:id resolve:resolve reject:reject];
}

#pragma mark - Events

- (void)fetchAllEvents:(NSString *)startDate
               endDate:(NSString *)endDate
           calendarIds:(NSArray *)calendarIds
               resolve:(RCTPromiseResolveBlock)resolve
                reject:(RCTPromiseRejectBlock)reject
{
  [_store fetchAllEvents:startDate endDate:endDate calendarIds:calendarIds resolve:resolve reject:reject];
}

- (void)findEventById:(NSString *)id
              resolve:(RCTPromiseResolveBlock)resolve
               reject:(RCTPromiseRejectBlock)reject
{
  [_store findEventById:id resolve:resolve reject:reject];
}

- (void)saveEvent:(NSString *)title
          details:(NSDictionary *)details
          options:(NSDictionary *)options
          resolve:(RCTPromiseResolveBlock)resolve
           reject:(RCTPromiseRejectBlock)reject
{
  [_store saveEvent:title details:details options:options resolve:resolve reject:reject];
}

- (void)removeEvent:(NSString *)id
            options:(NSDictionary *)options
            resolve:(RCTPromiseResolveBlock)resolve
             reject:(RCTPromiseRejectBlock)reject
{
  [_store removeEvent:id options:options resolve:resolve reject:reject];
}

#pragma mark - Android-only members

- (void)uriForCalendar:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  reject(@"E_UNSUPPORTED", @"uriForCalendar is only available on Android", nil);
}

- (void)openEventInCalendar:(NSString *)eventId
{
  // Android only. Intentional no-op on iOS.
}

@end
