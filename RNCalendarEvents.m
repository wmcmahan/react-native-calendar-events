#import "RNCalendarEvents.h"
#import "RCTConvert.h"
#import <EventKit/EventKit.h>

@interface RNCalendarEvents ()
@property (nonatomic, strong) EKEventStore *eventStore;
@property (copy, nonatomic) NSArray *calendarEvents;
@property (nonatomic) BOOL isAccessToEventStoreGranted;
@end

static NSString *const _id = @"id";
static NSString *const _title = @"title";
static NSString *const _location = @"location";
static NSString *const _startDate = @"startDate";
static NSString *const _endDate = @"endDate";
static NSString *const _allDay = @"allDay";
static NSString *const _notes = @"notes";
static NSString *const _alarms = @"alarms";
static NSString *const _recurrence = @"recurrence";
static NSString *const _occurrenceDate = @"occurrenceDate";
static NSString *const _isDetached = @"isDetached";

@implementation RNCalendarEvents

@synthesize bridge = _bridge;

RCT_EXPORT_MODULE()

#pragma mark -
#pragma mark Event Store Initialize

- (EKEventStore *)eventStore
{
    if (!_eventStore) {
        _eventStore = [[EKEventStore alloc] init];
    }
    return _eventStore;
}

- (NSArray *)calendarEvents
{
    if (!_calendarEvents) {
        _calendarEvents = [[NSArray alloc] init];
    }
    return _calendarEvents;
}

#pragma mark -
#pragma mark Event Store Authorization

- (void)authorizationStatusForAccessEventStore
{
    EKAuthorizationStatus status = [EKEventStore authorizationStatusForEntityType:EKEntityTypeEvent];
    
    switch (status) {
        case EKAuthorizationStatusDenied:
        case EKAuthorizationStatusRestricted: {
            self.isAccessToEventStoreGranted = NO;
            break;
        }
        case EKAuthorizationStatusAuthorized:
            self.isAccessToEventStoreGranted = YES;
            [self addNotificationCenter];
            break;
        case EKAuthorizationStatusNotDetermined: {
            [self requestCalendarAccess];
            break;
        }
    }
}

-(void)requestCalendarAccess
{
    __weak RNCalendarEvents *weakSelf = self;
    [self.eventStore requestAccessToEntityType:EKEntityTypeEvent completion:^(BOOL granted, NSError *error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            weakSelf.isAccessToEventStoreGranted = granted;
            [weakSelf addNotificationCenter];
        });
    }];
}

#pragma mark -
#pragma mark Event Store Accessors

- (void)addCalendarEvent:(NSString *)title
               startDate:(NSDate *)startDate
                 endDate:(NSDate *)endDate
                location:(NSString *)location
                   notes:(NSString *)notes
                  alarms:(NSArray *)alarms
              recurrence:(NSString *)recurrence
{
    if (!self.isAccessToEventStoreGranted) {
        return;
    }
    
    EKEvent *calendarEvent = [EKEvent eventWithEventStore:self.eventStore];
    calendarEvent.calendar = [self.eventStore defaultCalendarForNewEvents];
    calendarEvent.title = title;
    calendarEvent.location = location;
    calendarEvent.startDate = startDate;
    calendarEvent.endDate = endDate;
    calendarEvent.notes = notes;
    
    if (alarms) {
        calendarEvent.alarms = [self createCalendarEventAlarms:alarms];
    }
    
    if (recurrence) {
        EKRecurrenceRule *rule = [self createRecurrenceRule:recurrence];
        if (rule) {
            calendarEvent.recurrenceRules = [NSArray arrayWithObject:rule];
        }
    }
    
    [self saveEvent:calendarEvent];
}

- (void)editEvent:(EKEvent *)calendarEvent
            title:(NSString *)title
        startDate:(NSDate *)startDate
          endDate:(NSDate *)endDate
         location:(NSString *)location
            notes:(NSString *)notes
           alarms:(NSArray *)alarms
       recurrence:(NSString *)recurrence
{
    if (!self.isAccessToEventStoreGranted) {
        return;
    }
    
    calendarEvent.title = title;
    calendarEvent.location = location;
    calendarEvent.startDate = startDate;
    calendarEvent.endDate = endDate;
    calendarEvent.notes = notes;
    
    if (alarms) {
        calendarEvent.alarms = [self createCalendarEventAlarms:alarms];
    }
    
    if (recurrence) {
        EKRecurrenceRule *rule = [self createRecurrenceRule:recurrence];
        if (rule) {
            calendarEvent.recurrenceRules = [NSArray arrayWithObject:rule];
        }
    }
    
    [self saveEvent:calendarEvent];
}


-(void)saveEvent:(EKEvent *)calendarEvent
{
    NSError *error = nil;
    BOOL success = [self.eventStore saveEvent:calendarEvent span:EKSpanFutureEvents commit:YES error:&error];
    if (!success) {
        [self.bridge.eventDispatcher sendAppEventWithName:@"eventSaveError"
                                                     body:@{@"error": error}];
    } else {
        [self.bridge.eventDispatcher sendAppEventWithName:@"eventSaveSuccess"
                                                     body:calendarEvent.calendarItemIdentifier];
    }
}


- (void)deleteEvent:(NSString *)eventId
{
    if (!self.isAccessToEventStoreGranted) {
        return;
    }
    
    EKEvent *calendarEvent = (EKEvent *)[self.eventStore calendarItemWithIdentifier:eventId];
    NSError *error = nil;
    BOOL success = [self.eventStore removeEvent:calendarEvent span:EKSpanThisEvent commit:YES error:&error];
    
    if (!success) {
        [self.bridge.eventDispatcher sendAppEventWithName:@"CalendarEventError"
                                                     body:@{@"error": error}];
    }
}

#pragma mark -
#pragma mark Alarms

- (EKAlarm *)createCalendarEventAlarm:(NSDictionary *)alarm
{
    EKAlarm *calendarEventAlarm = nil;
    id alarmDate = [alarm valueForKey:@"date"];
    
    if ([alarmDate isKindOfClass:[NSString class]]) {
        calendarEventAlarm = [EKAlarm alarmWithAbsoluteDate:[RCTConvert NSDate:alarmDate]];
    } else if ([alarmDate isKindOfClass:[NSNumber class]]) {
        int minutes = [alarmDate intValue];
        calendarEventAlarm = [EKAlarm alarmWithRelativeOffset:(60 * minutes)];
    } else {
        calendarEventAlarm = [[EKAlarm alloc] init];
    }
    
    if ([alarm objectForKey:@"structuredLocation"] && [[alarm objectForKey:@"structuredLocation"] count]) {
        NSDictionary *locationOptions = [alarm valueForKey:@"structuredLocation"];
        NSDictionary *geo = [locationOptions valueForKey:@"coords"];
        CLLocation *geoLocation = [[CLLocation alloc] initWithLatitude:[[geo valueForKey:@"latitude"] doubleValue]
                                                             longitude:[[geo valueForKey:@"longitude"] doubleValue]];
        
        calendarEventAlarm.structuredLocation = [EKStructuredLocation locationWithTitle:[locationOptions valueForKey:@"title"]];
        calendarEventAlarm.structuredLocation.geoLocation = geoLocation;
        calendarEventAlarm.structuredLocation.radius = [[locationOptions valueForKey:@"radius"] doubleValue];
        
        if ([[locationOptions valueForKey:@"proximity"] isEqualToString:@"enter"]) {
            calendarEventAlarm.proximity = EKAlarmProximityEnter;
        } else if ([[locationOptions valueForKey:@"proximity"] isEqualToString:@"leave"]) {
            calendarEventAlarm.proximity = EKAlarmProximityLeave;
        } else {
            calendarEventAlarm.proximity = EKAlarmProximityNone;
        }
    }
    return calendarEventAlarm;
}

- (NSArray *)createCalendarEventAlarms:(NSArray *)alarms
{
    NSMutableArray *calendarEventAlarms = [[NSMutableArray alloc] init];
    for (NSDictionary *alarm in alarms) {
        if ([alarm count] && ([alarm valueForKey:@"date"] || [alarm objectForKey:@"structuredLocation"])) {
            EKAlarm *reminderAlarm = [self createCalendarEventAlarm:alarm];
            [calendarEventAlarms addObject:reminderAlarm];
        }
    }
    return [calendarEventAlarms copy];
}

- (void)addCalendarEventAlarm:(NSString *)eventId alarm:(NSDictionary *)alarm
{
    if (!self.isAccessToEventStoreGranted) {
        return;
    }
    
    EKEvent *calendarEvent = (EKEvent *)[self.eventStore calendarItemWithIdentifier:eventId];
    EKAlarm *calendarEventAlarm = [self createCalendarEventAlarm:alarm];
    [calendarEvent addAlarm:calendarEventAlarm];
    
    [self saveEvent:calendarEvent];
}


- (void)addCalendarEventAlarms:(NSString *)eventId alarms:(NSArray *)alarms
{
    if (!self.isAccessToEventStoreGranted) {
        return;
    }
    
    EKEvent *calendarEvent = (EKEvent *)[self.eventStore calendarItemWithIdentifier:eventId];
    calendarEvent.alarms = [self createCalendarEventAlarms:alarms];
    
    [self saveEvent:calendarEvent];
}

#pragma mark -
#pragma mark RecurrenceRules

-(EKRecurrenceFrequency)frequencyMatchingName:(NSString *)name
{
    EKRecurrenceFrequency recurrence = EKRecurrenceFrequencyDaily;
    
    if ([name isEqualToString:@"weekly"]) {
        recurrence = EKRecurrenceFrequencyWeekly;
    } else if ([name isEqualToString:@"monthly"]) {
        recurrence = EKRecurrenceFrequencyMonthly;
    } else if ([name isEqualToString:@"yearly"]) {
        recurrence = EKRecurrenceFrequencyYearly;
    }
    return recurrence;
}

-(EKRecurrenceRule *)createRecurrenceRule:(NSString *)frequency
{
    EKRecurrenceRule *rule = nil;
    NSArray *validFrequencyTypes = @[@"daily", @"weekly", @"monthly", @"yearly"];
    
    if ([validFrequencyTypes containsObject:frequency]) {
        rule = [[EKRecurrenceRule alloc] initRecurrenceWithFrequency:[self frequencyMatchingName:frequency]
                                                            interval:1
                                                                 end:nil];
    }
    return rule;
}

-(NSString *)nameMatchingFrequency:(EKRecurrenceFrequency)frequency
{
    switch (frequency) {
        case EKRecurrenceFrequencyWeekly:
            return @"weekly";
        case EKRecurrenceFrequencyMonthly:
            return @"monthly";
        case EKRecurrenceFrequencyYearly:
            return @"yearly";
        default:
            return @"daily";
    }
}

#pragma mark -
#pragma mark Serializers

- (NSArray *)serializeCalendarEvents:(NSArray *)calendarEvents
{
    NSMutableArray *serializedCalendarEvents = [[NSMutableArray alloc] init];
    
    NSDictionary *emptyCalendarEvent = @{
                                         _title: @"",
                                         _location: @"",
                                         _startDate: @"",
                                         _endDate: @"",
                                         _allDay: @NO,
                                         _notes: @"",
                                         _alarms: @[],
                                         _recurrence: @""
                                         };
    
    NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
    NSTimeZone *timeZone = [NSTimeZone timeZoneWithName:@"UTC"];
    [dateFormatter setTimeZone:timeZone];
    [dateFormatter setLocale:[NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"]];
    [dateFormatter setDateFormat: @"yyyy-MM-dd'T'HH:mm:ss.SSS'Z"];
    
    for (EKEvent *event in calendarEvents) {
        
        NSMutableDictionary *formedCalendarEvent = [NSMutableDictionary dictionaryWithDictionary:emptyCalendarEvent];
        
        if (event.calendarItemIdentifier) {
            [formedCalendarEvent setValue:event.calendarItemIdentifier forKey:_id];
        }
        
        if (event.title) {
            [formedCalendarEvent setValue:event.title forKey:_title];
        }
        
        if (event.notes) {
            [formedCalendarEvent setValue:event.notes forKey:_notes];
        }
        
        if (event.location) {
            [formedCalendarEvent setValue:event.location forKey:_location];
        }
        
        if (event.hasAlarms) {
            NSMutableArray *alarms = [[NSMutableArray alloc] init];
            
            for (EKAlarm *alarm in event.alarms) {
                
                NSMutableDictionary *formattedAlarm = [[NSMutableDictionary alloc] init];
                NSString *alarmDate = nil;
                
                if (alarm.absoluteDate) {
                    alarmDate = [dateFormatter stringFromDate:alarm.absoluteDate];
                } else if (alarm.relativeOffset) {
                    NSDate *calendarEventStartDate = nil;
                    if (event.startDate) {
                        calendarEventStartDate = event.startDate;
                    } else {
                        calendarEventStartDate = [NSDate date];
                    }
                    alarmDate = [dateFormatter stringFromDate:[NSDate dateWithTimeInterval:alarm.relativeOffset
                                                                                 sinceDate:calendarEventStartDate]];
                }
                [formattedAlarm setValue:alarmDate forKey:@"date"];
                
                if (alarm.structuredLocation) {
                    NSString *proximity = nil;
                    switch (alarm.proximity) {
                        case EKAlarmProximityEnter:
                            proximity = @"enter";
                            break;
                        case EKAlarmProximityLeave:
                            proximity = @"leave";
                            break;
                        default:
                            proximity = @"None";
                            break;
                    }
                    [formattedAlarm setValue:@{
                                               @"title": alarm.structuredLocation.title,
                                               @"proximity": proximity,
                                               @"radius": @(alarm.structuredLocation.radius),
                                               @"coords": @{
                                                       @"latitude": @(alarm.structuredLocation.geoLocation.coordinate.latitude),
                                                       @"longitude": @(alarm.structuredLocation.geoLocation.coordinate.longitude)
                                                       }}
                                      forKey:@"structuredLocation"];
                    
                }
                [alarms addObject:formattedAlarm];
            }
            [formedCalendarEvent setValue:alarms forKey:_alarms];
        }
        
        if (event.startDate) {
            [formedCalendarEvent setValue:[dateFormatter stringFromDate:event.startDate] forKey:_startDate];
        }
        
        if (event.endDate) {
            [formedCalendarEvent setValue:[dateFormatter stringFromDate:event.endDate] forKey:_endDate];
        }
        
        if (event.occurrenceDate) {
            [formedCalendarEvent setValue:[dateFormatter stringFromDate:event.occurrenceDate] forKey:_occurrenceDate];
        }
        
        
        [formedCalendarEvent setValue:[NSNumber numberWithBool:event.isDetached] forKey:_isDetached];
        
        [formedCalendarEvent setValue:[NSNumber numberWithBool:event.allDay] forKey:_allDay];
        
        if (event.hasRecurrenceRules) {
            NSString *frequencyType = [self nameMatchingFrequency:[[event.recurrenceRules objectAtIndex:0] frequency]];
            [formedCalendarEvent setValue:frequencyType forKey:_recurrence];
        }
        
        [serializedCalendarEvents addObject:formedCalendarEvent];
    }
    
    return [serializedCalendarEvents copy];
}

#pragma mark -
#pragma mark notifications

- (void)addNotificationCenter
{
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(calendarEventUpdateReceived:)
                                                 name:EKEventStoreChangedNotification
                                               object:nil];
}

- (void)calendarEventUpdateReceived:(NSNotification *)notification
{
    __weak RNCalendarEvents *weakSelf = self;
    dispatch_async(dispatch_get_main_queue(), ^{
        [weakSelf.bridge.eventDispatcher sendAppEventWithName:@"calendarEventsChanged"
                                                         body:nil];
    });
}

- (void) dealloc
{
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

#pragma mark -
#pragma mark RCT Exports

RCT_EXPORT_METHOD(authorizeEventStore:(RCTResponseSenderBlock)callback)
{
    [self authorizationStatusForAccessEventStore];
    callback(@[@(self.isAccessToEventStoreGranted)]);
}

RCT_EXPORT_METHOD(fetchAllEvents:(NSDate *)startDate endDate:(NSDate *)endDate callback:(RCTResponseSenderBlock)callback)
{
    NSPredicate *predicate = [self.eventStore predicateForEventsWithStartDate:startDate
                                                                      endDate:endDate
                                                                    calendars:nil];
    
    __weak RNCalendarEvents *weakSelf = self;
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        weakSelf.calendarEvents = [weakSelf.eventStore eventsMatchingPredicate:predicate];
        dispatch_async(dispatch_get_main_queue(), ^{
            callback(@[[weakSelf serializeCalendarEvents:weakSelf.calendarEvents]]);
        });
    });
}

RCT_EXPORT_METHOD(saveEvent:(NSString *)title details:(NSDictionary *)details)
{
    NSString *eventId = [RCTConvert NSString:details[_id]];
    NSString *location = [RCTConvert NSString:details[_location]];
    NSDate *startDate = [RCTConvert NSDate:details[_startDate]];
    NSDate *endDate = [RCTConvert NSDate:details[_endDate]];
    NSString *notes = [RCTConvert NSString:details[_notes]];
    NSArray *alarms = [RCTConvert NSArray:details[_alarms]];
    NSString *recurrence = [RCTConvert NSString:details[_recurrence]];
    
    if (eventId) {
        EKEvent *calendarEvent = (EKEvent *)[self.eventStore calendarItemWithIdentifier:eventId];
        [self editEvent:calendarEvent
                  title:title
              startDate:startDate
                endDate:endDate
               location:location
                  notes:notes
                 alarms:alarms
             recurrence:recurrence];
        
    } else {
        [self addCalendarEvent:title
                     startDate:startDate
                       endDate:endDate
                      location:location
                         notes:notes
                        alarms:alarms
                    recurrence:recurrence];
    }
}

RCT_EXPORT_METHOD(removeEvent:(NSString *)eventId)
{
    [self deleteEvent:eventId];
}

@end
