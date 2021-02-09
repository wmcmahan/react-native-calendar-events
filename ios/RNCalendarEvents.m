#import "RNCalendarEvents.h"
#import <React/RCTConvert.h>
#import <React/RCTUtils.h>
#import <EventKit/EventKit.h>

@interface RNCalendarEvents ()
@property (nonatomic, readonly) EKEventStore *eventStore;
@end

static NSString *const _id = @"id";
static NSString *const _calendarId = @"calendarId";
static NSString *const _title = @"title";
static NSString *const _location = @"location";
static NSString *const _startDate = @"startDate";
static NSString *const _endDate = @"endDate";
static NSString *const _allDay = @"allDay";
static NSString *const _notes = @"notes";
static NSString *const _url = @"url";
static NSString *const _alarms = @"alarms";
static NSString *const _recurrence = @"recurrence";
static NSString *const _recurrenceRule = @"recurrenceRule";
static NSString *const _occurrenceDate = @"occurrenceDate";
static NSString *const _isDetached = @"isDetached";
static NSString *const _availability = @"availability";
static NSString *const _attendees    = @"attendees";
static NSString *const _timeZone    = @"timeZone";

dispatch_queue_t serialQueue;

@implementation RNCalendarEvents

- (NSError *)exceptionToError:(NSException *)exception {
    NSMutableDictionary * info = [NSMutableDictionary dictionary];
    [info setValue:exception.name forKey:@"ExceptionName"];
    [info setValue:exception.reason forKey:@"ExceptionReason"];
    [info setValue:exception.callStackReturnAddresses forKey:@"ExceptionCallStackReturnAddresses"];
    [info setValue:exception.callStackSymbols forKey:@"ExceptionCallStackSymbols"];
    [info setValue:exception.userInfo forKey:@"ExceptionUserInfo"];

    NSError *error = [[NSError alloc]
                      initWithDomain:@"RNCalendarEvents"
                      code:-1
                      userInfo:info
                      ];
    return error;
}

- (NSString *)hexStringFromColor:(UIColor *)color {
    const CGFloat *components = CGColorGetComponents(color.CGColor);

    CGFloat r;
    CGFloat g;
    CGFloat b;
    if(components && sizeof(components) >= 3){
        r = components[0];
        g = components[1];
        b = components[2];
    }else{
        r = 1;
        g = 1;
        b = 1;
    }
    
    return [NSString stringWithFormat:@"#%02lX%02lX%02lX",
            lroundf(r * 255),
            lroundf(g * 255),
            lroundf(b * 255)];
}

@synthesize bridge = _bridge;

RCT_EXPORT_MODULE()

#pragma mark -
#pragma mark Event Store Initialize

- (instancetype)init {
    self = [super init];
    if (self) {
        _eventStore = [[EKEventStore alloc] init];
        serialQueue = dispatch_queue_create("rncalendarevents.queue", DISPATCH_QUEUE_SERIAL);
    }
    return self;
}

#pragma mark -
#pragma mark Event Store Authorization

+ (BOOL)requiresMainQueueSetup
{
    return NO;
}

- (BOOL)isCalendarAccessGranted
{
    EKAuthorizationStatus status = [EKEventStore authorizationStatusForEntityType:EKEntityTypeEvent];

    return status == EKAuthorizationStatusAuthorized;
}

#pragma mark -
#pragma mark Event Store Accessors

- (NSDictionary *)buildAndSaveEvent:(NSDictionary *)details options:(NSDictionary *)options
{
    EKEvent *calendarEvent = nil;
    NSString *calendarId = [RCTConvert NSString:details[_calendarId]];
    NSString *eventId = [RCTConvert NSString:details[_id]];
    NSString *title = [RCTConvert NSString:details[_title]];
    NSString *location = [RCTConvert NSString:details[_location]];
    NSDate *startDate = [RCTConvert NSDate:details[_startDate]];
    NSDate *endDate = [RCTConvert NSDate:details[_endDate]];
    NSNumber *allDay = [RCTConvert NSNumber:details[_allDay]];
    NSString *notes = [RCTConvert NSString:details[_notes]];
    NSString *url = [RCTConvert NSString:details[_url]];
    NSArray *alarms = [RCTConvert NSArray:details[_alarms]];
    NSString *recurrence = [RCTConvert NSString:details[_recurrence]];
    NSDictionary *recurrenceRule = [RCTConvert NSDictionary:details[_recurrenceRule]];
    NSString *availability = [RCTConvert NSString:details[_availability]];
    NSString *timeZone = [RCTConvert NSString:details[_timeZone]];

    if (eventId) {
        calendarEvent = (EKEvent *)[self.eventStore calendarItemWithIdentifier:eventId];

    } else {
        calendarEvent = [EKEvent eventWithEventStore:self.eventStore];
        calendarEvent.calendar = [self.eventStore defaultCalendarForNewEvents];
        calendarEvent.timeZone = [NSTimeZone defaultTimeZone];

        if (calendarId) {
            EKCalendar *calendar = [self.eventStore calendarWithIdentifier:calendarId];

            if (calendar) {
                calendarEvent.calendar = calendar;
            }
        }
    }

    if (timeZone) {
      calendarEvent.timeZone = [NSTimeZone timeZoneWithName:timeZone];
    }

    if (title) {
        calendarEvent.title = title;
    }

    if (location) {
        calendarEvent.location = location;
    }

    if (startDate) {
        calendarEvent.startDate = startDate;
    }

    if (endDate) {
        calendarEvent.endDate = endDate;
    }

    if (allDay) {
        calendarEvent.allDay = [allDay boolValue];
    }

    if (notes) {
        calendarEvent.notes = notes;
    }

    if (alarms) {
        calendarEvent.alarms = [self createCalendarEventAlarms:alarms];
    }

    if (recurrence) {
        EKRecurrenceRule *rule = [self createRecurrenceRule:recurrence interval:0 occurrence:0 endDate:nil days: nil weekPositionInMonth: 0];
        if (rule) {
            calendarEvent.recurrenceRules = [NSArray arrayWithObject:rule];
        }
    }

    if (recurrenceRule) {
        NSString *frequency = [RCTConvert NSString:recurrenceRule[@"frequency"]];
        NSInteger interval = [RCTConvert NSInteger:recurrenceRule[@"interval"]];
        NSInteger occurrence = [RCTConvert NSInteger:recurrenceRule[@"occurrence"]];
        NSDate *endDate = [RCTConvert NSDate:recurrenceRule[@"endDate"]];
        NSArray *daysOfWeek = [RCTConvert NSArray:recurrenceRule[@"daysOfWeek"]];
        NSInteger weekPositionInMonth = [RCTConvert NSInteger:recurrenceRule[@"weekPositionInMonth"]];

        EKRecurrenceRule *rule = [self createRecurrenceRule:frequency interval:interval occurrence:occurrence endDate:endDate days:daysOfWeek weekPositionInMonth: weekPositionInMonth];
        if (rule) {
            calendarEvent.recurrenceRules = [NSArray arrayWithObject:rule];
        } else {
            calendarEvent.recurrenceRules = nil;
        }
    }


    if (availability) {
        calendarEvent.availability = [self availablilityConstantMatchingString:availability];
    }

    NSURL *URL = [NSURL URLWithString:[url stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLQueryAllowedCharacterSet]]];
    if (URL) {
        calendarEvent.URL = URL;
    }

    if ([details objectForKey:@"structuredLocation"] && [[details objectForKey:@"structuredLocation"] count]) {
        NSDictionary *locationOptions = [details valueForKey:@"structuredLocation"];
        NSDictionary *geo = [locationOptions valueForKey:@"coords"];
        CLLocation *geoLocation = [[CLLocation alloc] initWithLatitude:[[geo valueForKey:@"latitude"] doubleValue]
                                                             longitude:[[geo valueForKey:@"longitude"] doubleValue]];
        
        calendarEvent.structuredLocation = [EKStructuredLocation locationWithTitle:[locationOptions valueForKey:@"title"]];
        calendarEvent.structuredLocation.geoLocation = geoLocation;
        calendarEvent.structuredLocation.radius = [[locationOptions valueForKey:@"radius"] doubleValue];
    }
    
    return [self saveEvent:calendarEvent options:options];
}

- (NSDictionary *)saveEvent:(EKEvent *)calendarEvent options:(NSDictionary *)options
{
    NSMutableDictionary *response = [NSMutableDictionary dictionaryWithDictionary:@{@"success": [NSNull null], @"error": [NSNull null]}];
    NSDate *exceptionDate = [RCTConvert NSDate:options[@"exceptionDate"]];
    EKSpan eventSpan = EKSpanFutureEvents;

    if (exceptionDate) {
        calendarEvent.startDate = exceptionDate;
        eventSpan = EKSpanThisEvent;
    }

    NSError *error = nil;
    BOOL success = [self.eventStore saveEvent:calendarEvent span:eventSpan commit:YES error:&error];

    if (!success) {
        [response setValue:[error.userInfo valueForKey:@"NSLocalizedDescription"] forKey:@"error"];
    } else {
        [response setValue:calendarEvent.calendarItemIdentifier forKey:@"success"];
    }
    return [response copy];
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

- (void)addCalendarEventAlarm:(NSString *)eventId alarm:(NSDictionary *)alarm options:(NSDictionary *)options
{
    EKEvent *calendarEvent = (EKEvent *)[self.eventStore calendarItemWithIdentifier:eventId];
    EKAlarm *calendarEventAlarm = [self createCalendarEventAlarm:alarm];
    [calendarEvent addAlarm:calendarEventAlarm];

    [self saveEvent:calendarEvent options:options];
}

- (void)addCalendarEventAlarms:(NSString *)eventId alarms:(NSArray *)alarms options:(NSDictionary *)options
{
    EKEvent *calendarEvent = (EKEvent *)[self.eventStore calendarItemWithIdentifier:eventId];
    calendarEvent.alarms = [self createCalendarEventAlarms:alarms];

    [self saveEvent:calendarEvent options:options];
}

#pragma mark -
#pragma mark RecurrenceRules

-(EKRecurrenceFrequency)frequencyMatchingName:(NSString *)name
{
    EKRecurrenceFrequency recurrence = nil;

    if ([name isEqualToString:@"weekly"]) {
        recurrence = EKRecurrenceFrequencyWeekly;
    } else if ([name isEqualToString:@"monthly"]) {
        recurrence = EKRecurrenceFrequencyMonthly;
    } else if ([name isEqualToString:@"yearly"]) {
        recurrence = EKRecurrenceFrequencyYearly;
    } else if ([name isEqualToString:@"daily"]) {
        recurrence = EKRecurrenceFrequencyDaily;
    }
    return recurrence;
}

-(EKRecurrenceDayOfWeek *) dayOfTheWeekMatchingName: (NSString *) day
{
    EKRecurrenceDayOfWeek *weekDay = nil;

    if ([day isEqualToString:@"MO"]) {
        weekDay = [EKRecurrenceDayOfWeek dayOfWeek:2];
    } else if ([day isEqualToString:@"TU"]) {
        weekDay = [EKRecurrenceDayOfWeek dayOfWeek:3];
    } else if ([day isEqualToString:@"WE"]) {
        weekDay = [EKRecurrenceDayOfWeek dayOfWeek:4];
    } else if ([day isEqualToString:@"TH"]) {
        weekDay = [EKRecurrenceDayOfWeek dayOfWeek:5];
    } else if ([day isEqualToString:@"FR"]) {
        weekDay = [EKRecurrenceDayOfWeek dayOfWeek:6];
    } else if ([day isEqualToString:@"SA"]) {
        weekDay = [EKRecurrenceDayOfWeek dayOfWeek:7];
    } else if ([day isEqualToString:@"SU"]) {
        weekDay = [EKRecurrenceDayOfWeek dayOfWeek:1];
    }

    NSLog(@"%s", "dayOfTheWeek");
    NSLog(@"%@", weekDay);
    return weekDay;
}

-(NSMutableArray *) createRecurrenceDaysOfWeek: (NSArray *) days
{
    NSMutableArray *daysOfTheWeek = nil;

    if (days.count) {
        daysOfTheWeek = [[NSMutableArray alloc] init];

        for (NSString *day in days) {
            EKRecurrenceDayOfWeek *weekDay = [self dayOfTheWeekMatchingName: day];
            [daysOfTheWeek addObject:weekDay];

        }
    }

    return daysOfTheWeek;
}

-(EKRecurrenceRule *)createRecurrenceRule:(NSString *)frequency interval:(NSInteger)interval occurrence:(NSInteger)occurrence endDate:(NSDate *)endDate days:(NSArray *)days weekPositionInMonth:(NSInteger) weekPositionInMonth
{
    EKRecurrenceRule *rule = nil;
    EKRecurrenceEnd *recurrenceEnd = nil;
    NSInteger recurrenceInterval = 1;
    NSArray *validFrequencyTypes = @[@"daily", @"weekly", @"monthly", @"yearly"];
    NSArray *daysOfTheWeekRecurrence = [self createRecurrenceDaysOfWeek:days];
    NSMutableArray *setPositions = nil;

    if (frequency && [validFrequencyTypes containsObject:frequency]) {

        if (endDate) {
            recurrenceEnd = [EKRecurrenceEnd recurrenceEndWithEndDate:endDate];
        } else if (occurrence && occurrence > 0) {
            recurrenceEnd = [EKRecurrenceEnd recurrenceEndWithOccurrenceCount:occurrence];
        }

        if (interval > 1) {
            recurrenceInterval = interval;
        }

        if (weekPositionInMonth > 0) {
            setPositions = [NSMutableArray array];
            [setPositions addObject:[NSNumber numberWithInteger: weekPositionInMonth ]];
        }
        rule = [[EKRecurrenceRule alloc] initRecurrenceWithFrequency:[self frequencyMatchingName:frequency]
                                                            interval:recurrenceInterval
                                                                 daysOfTheWeek:daysOfTheWeekRecurrence
                                                                 daysOfTheMonth:nil
                                                                 monthsOfTheYear:nil
                                                                 weeksOfTheYear:nil
                                                                 daysOfTheYear:nil
                                                                 setPositions:setPositions
                                                                 end:recurrenceEnd];
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
        case EKRecurrenceFrequencyDaily:
            return @"daily";
        default:
            return @"";
    }
}

#pragma mark -
#pragma mark Availability

- (NSMutableArray *)calendarSupportedAvailabilitiesFromMask:(EKCalendarEventAvailabilityMask)types
{
    NSMutableArray *availabilitiesStrings = [[NSMutableArray alloc] init];

    if(types & EKCalendarEventAvailabilityBusy) [availabilitiesStrings addObject:@"busy"];
    if(types & EKCalendarEventAvailabilityFree) [availabilitiesStrings addObject:@"free"];
    if(types & EKCalendarEventAvailabilityTentative) [availabilitiesStrings addObject:@"tentative"];
    if(types & EKCalendarEventAvailabilityUnavailable) [availabilitiesStrings addObject:@"unavailable"];

    return availabilitiesStrings;
}

- (NSString *)availabilityStringMatchingConstant:(EKEventAvailability)constant
{
    switch(constant) {
        case EKEventAvailabilityNotSupported:
            return @"notSupported";
        case EKEventAvailabilityBusy:
            return @"busy";
        case EKEventAvailabilityFree:
            return @"free";
        case EKEventAvailabilityTentative:
            return @"tentative";
        case EKEventAvailabilityUnavailable:
            return @"unavailable";
        default:
            return @"notSupported";
    }
}

- (EKEventAvailability)availablilityConstantMatchingString:(NSString *)string
{
    if([string isEqualToString:@"busy"]) {
        return EKEventAvailabilityBusy;
    }

    if([string isEqualToString:@"free"]) {
        return EKEventAvailabilityFree;
    }

    if([string isEqualToString:@"tentative"]) {
        return EKEventAvailabilityTentative;
    }

    if([string isEqualToString:@"unavailable"]) {
        return EKEventAvailabilityUnavailable;
    }

    return EKEventAvailabilityNotSupported;
}

#pragma mark -
#pragma mark Serializers

- (NSArray *)serializeCalendarEvents:(NSArray *)calendarEvents
{
    NSMutableArray *serializedCalendarEvents = [[NSMutableArray alloc] init];

    for (EKEvent *event in calendarEvents) {
        [serializedCalendarEvents addObject:[self serializeCalendarEvent:event]];
    }

    return [serializedCalendarEvents copy];
}

- (NSDictionary *)serializeCalendarEvent:(EKEvent *)event
{

    NSDictionary *emptyCalendarEvent = @{
                                         _title: @"",
                                         _location: @"",
                                         _startDate: @"",
                                         _endDate: @"",
                                         _allDay: @NO,
                                         _notes: @"",
                                         _url: @"",
                                         _alarms: [NSArray array],
                                         _attendees: [NSArray array],
                                         _recurrence: @"",
                                         _recurrenceRule: @{
                                                 @"frequency": @"",
                                                 @"interval": @"",
                                                 @"occurrence": @"",
                                                 @"endDate": @""
                                                 },
                                         _availability: @"",
                                         _timeZone: @""
                                         };

    NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
    NSTimeZone *timeZone = [NSTimeZone timeZoneWithName:@"UTC"];
    [dateFormatter setTimeZone:timeZone];
    [dateFormatter setLocale:[NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"]];
    [dateFormatter setDateFormat: @"yyyy-MM-dd'T'HH:mm:ss.SSS'Z"];


    NSMutableDictionary *formedCalendarEvent = [NSMutableDictionary dictionaryWithDictionary:emptyCalendarEvent];

    if (event.calendarItemIdentifier) {
        [formedCalendarEvent setValue:event.calendarItemIdentifier forKey:_id];
    }

    if (event.calendar) {
        [formedCalendarEvent setValue:@{
                                        @"id": event.calendar.calendarIdentifier?event.calendar.calendarIdentifier: @"tempCalendar",
                                        @"title": event.calendar.title ? event.calendar.title : @"",
                                        @"source": event.calendar.source && event.calendar.source.title ? event.calendar.source.title : @"",
                                        @"allowsModifications": @(event.calendar.allowsContentModifications),
                                        @"allowedAvailabilities": [self calendarSupportedAvailabilitiesFromMask:event.calendar.supportedEventAvailabilities],
                                        @"color": [self hexStringFromColor:[UIColor colorWithCGColor:event.calendar.CGColor]]
                                        }
                               forKey:@"calendar"];
    }

    if (event.title) {
        [formedCalendarEvent setValue:event.title forKey:_title];
    }

    if (event.notes) {
        [formedCalendarEvent setValue:event.notes forKey:_notes];
    }

    if (event.URL) {
        [formedCalendarEvent setValue:[event.URL absoluteString] forKey:_url];
    }

    if (event.location) {
        [formedCalendarEvent setValue:event.location forKey:_location];
    }

    if (event.timeZone) {
        [formedCalendarEvent setValue:event.timeZone forKey:_timeZone];
    }

    @try {
        if (event.attendees) {
            NSMutableArray *attendees = [[NSMutableArray alloc] init];
            for (EKParticipant *attendee in event.attendees) {

                NSMutableDictionary *descriptionData = [NSMutableDictionary dictionary];
                for (NSString *pairString in [attendee.description componentsSeparatedByString:@";"])
                {
                    NSArray *pair = [pairString componentsSeparatedByString:@"="];
                    if ( [pair count] != 2)
                        continue;
                    [descriptionData setObject:[[pair objectAtIndex:1] stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]] forKey:[[pair objectAtIndex:0]stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]]];
                }

                NSMutableDictionary *formattedAttendee = [[NSMutableDictionary alloc] init];
                NSString *name = [descriptionData valueForKey:@"name"];
                NSString *email = [descriptionData valueForKey:@"email"];
                NSString *phone = [descriptionData valueForKey:@"phone"];

                if(email && ![email isEqualToString:@"(null)"]) {
                    [formattedAttendee setValue:email forKey:@"email"];
                }
                else {
                    [formattedAttendee setValue:@"" forKey:@"email"];
                }
                if(phone && ![phone isEqualToString:@"(null)"]) {
                    [formattedAttendee setValue:phone forKey:@"phone"];
                }
                else {
                    [formattedAttendee setValue:@"" forKey:@"phone"];
                }
                if(name && ![name isEqualToString:@"(null)"]) {
                    [formattedAttendee setValue:name forKey:@"name"];
                }
                else {
                    [formattedAttendee setValue:@"" forKey:@"name"];
                }
                [attendees addObject:formattedAttendee];
            }
            [formedCalendarEvent setValue:attendees forKey:_attendees];
        }
    }
    @catch (NSException *exception) {
        NSLog(@"RNCalendarEvents encountered an issue while serializing event (attendees) '%@': %@", event.title, exception.reason);
    }
    
    @try {
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
                    NSMutableDictionary *structuredLocation = [[NSMutableDictionary alloc] initWithCapacity:4];
                    [structuredLocation addEntriesFromDictionary: @{
                                                                    @"title": alarm.structuredLocation.title
                                                                        ? alarm.structuredLocation.title : @"",
                                                                    @"proximity": proximity,
                                                                    @"radius": @(alarm.structuredLocation.radius)
                                                                    }];
                    if (alarm.structuredLocation.geoLocation) {
                        [structuredLocation setValue: @{
                                                        @"latitude": @(alarm.structuredLocation.geoLocation.coordinate.latitude),
                                                        @"longitude": @(alarm.structuredLocation.geoLocation.coordinate.longitude)
                                                        }
                                              forKey:@"coords"];
                    }
                    [formattedAlarm setValue:structuredLocation forKey:@"structuredLocation"];
                }
                [alarms addObject:formattedAlarm];
            }
            [formedCalendarEvent setValue:alarms forKey:_alarms];
        }
    }
    @catch (NSException *exception) {
        NSLog(@"RNCalendarEvents encountered an issue while serializing event (alarms) '%@': %@", event.title, exception.reason);
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
    
    @try {
        if (event.hasRecurrenceRules) {
            EKRecurrenceRule *rule = [event.recurrenceRules objectAtIndex:0];
            NSString *frequencyType = [self nameMatchingFrequency:[rule frequency]];
            [formedCalendarEvent setValue:frequencyType forKey:_recurrence];

            NSMutableDictionary *recurrenceRule = [NSMutableDictionary dictionaryWithDictionary:@{@"frequency": frequencyType}];

            if ([rule interval]) {
                [recurrenceRule setValue:@([rule interval]) forKey:@"interval"];
            }

            if ([[rule recurrenceEnd] endDate]) {
                [recurrenceRule setValue:[dateFormatter stringFromDate:[[rule recurrenceEnd] endDate]] forKey:@"endDate"];
            }

            if ([[rule recurrenceEnd] occurrenceCount]) {
                [recurrenceRule setValue:@([[rule recurrenceEnd] occurrenceCount]) forKey:@"occurrence"];
            }

            [formedCalendarEvent setValue:recurrenceRule forKey:_recurrenceRule];
        }
    }
    @catch (NSException *exception) {
        NSLog(@"RNCalendarEvents encountered an issue while serializing event (recurrenceRules) '%@': %@", event.title, exception.reason);
    }
    
    [formedCalendarEvent setValue:[self availabilityStringMatchingConstant:event.availability] forKey:_availability];
    
    @try {
        if (event.structuredLocation && event.structuredLocation.radius) {
            NSMutableDictionary *structuredLocation = [[NSMutableDictionary alloc] initWithCapacity:3];
            [structuredLocation addEntriesFromDictionary: @{
                                                            @"title": event.structuredLocation.title
                                                                ? event.structuredLocation.title : @"",
                                                            @"radius": @(event.structuredLocation.radius)
                                                            }];
            if (event.structuredLocation.geoLocation) {
                [structuredLocation setValue: @{
                                                @"latitude": @(event.structuredLocation.geoLocation.coordinate.latitude),
                                                @"longitude": @(event.structuredLocation.geoLocation.coordinate.longitude)
                                                }
                                    forKey:@"coords"];
            }
            [formedCalendarEvent setValue: structuredLocation forKey:@"structuredLocation"];
        }
    }
    @catch (NSException *exception) {
        NSLog(@"RNCalendarEvents encountered an issue while serializing event (structuredLocation) '%@': %@", event.title, exception.reason);
    }

    return [formedCalendarEvent copy];
}

#pragma mark -
#pragma mark RCT Exports

RCT_EXPORT_METHOD(checkPermissions:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    NSString *status;
    EKAuthorizationStatus authStatus = [EKEventStore authorizationStatusForEntityType:EKEntityTypeEvent];

    switch (authStatus) {
        case EKAuthorizationStatusDenied:
            status = @"denied";
            break;
        case EKAuthorizationStatusRestricted:
            status = @"restricted";
            break;
        case EKAuthorizationStatusAuthorized:
            status = @"authorized";
            break;
        default:
            status = @"undetermined";
            break;
    }

    resolve(status);
}

RCT_EXPORT_METHOD(requestPermissions:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    [self.eventStore requestAccessToEntityType:EKEntityTypeEvent completion:^(BOOL granted, NSError *error) {
        NSString *status = granted ? @"authorized" : @"denied";
        if (!error) {
            resolve(status);
        } else {
            reject(@"error", @"authorization request error", error);
        }
    }];
}

RCT_EXPORT_METHOD(findCalendars:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    if (![self isCalendarAccessGranted]) {
        reject(@"error", @"unauthorized to access calendar", nil);
        return;
    }

    @try {
        NSArray* calendars = [self.eventStore calendarsForEntityType:EKEntityTypeEvent];

        if (!calendars) {
            reject(@"error", @"error finding calendars", nil);
        } else {
            NSMutableArray *eventCalendars = [[NSMutableArray alloc] init];
            for (EKCalendar *calendar in calendars) {
                BOOL isPrimary = [calendar isEqual:[self.eventStore defaultCalendarForNewEvents]];
                [eventCalendars addObject:@{
                                            @"id": calendar.calendarIdentifier,
                                            @"title": calendar.title ? calendar.title : @"",
                                            @"allowsModifications": @(calendar.allowsContentModifications),
                                            @"source": calendar.source && calendar.source.title ? calendar.source.title : @"",
                                            @"isPrimary": @(isPrimary),
                                            @"allowedAvailabilities": [self calendarSupportedAvailabilitiesFromMask:calendar.supportedEventAvailabilities],
                                            @"color": [self hexStringFromColor:[UIColor colorWithCGColor:calendar.CGColor]]
                                            }];
            }
            resolve(eventCalendars);
        }
    }
    @catch (NSException *exception) {
        reject(@"error", @"saveCalendar error", [self exceptionToError:exception]);
    }
}

RCT_EXPORT_METHOD(saveCalendar:(NSDictionary *)options resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    if (![self isCalendarAccessGranted]) {
        return reject(@"error", @"unauthorized to access calendar", nil);
    }
    
    @try {
        EKCalendar *calendar = nil;
        EKSource *calendarSource = nil;
        NSString *title = [RCTConvert NSString:options[@"title"]];
        NSNumber *color = [RCTConvert NSNumber:options[@"color"]];
        NSString *type = [RCTConvert NSString:options[@"entityType"]];

        // First: Check if the user has an iCloud source set-up.
        for (EKSource *source in self.eventStore.sources) {
            if (source.sourceType == EKSourceTypeCalDAV && [source.title isEqualToString:@"iCloud"]) {
                calendarSource = source;
                break;
           }
        }

        // Second: If no iCloud source is set-up / utilised, then fall back and use the local source.
        if (calendarSource == nil) {
            for (EKSource *source in self.eventStore.sources) {
                if (source.sourceType == EKSourceTypeSubscribed || source.sourceType == EKSourceTypeLocal) { // if there is another source(ex: gmail) we need to fallback to EKSourceTypeSubscribed
                    calendarSource = source;
                    if (source.sourceType == EKSourceTypeLocal) {
                        break;
                    }
                }
            }
        }

        if (calendarSource == nil) {
            return reject(@"error", @"no source found to create the calendar (local & icloud)", nil);
        }

        if ([type isEqualToString:@"event"]) {
        calendar = [EKCalendar calendarForEntityType:EKEntityTypeEvent eventStore:self.eventStore];
        } else if ([type isEqualToString:@"reminder"]) {
          calendar = [EKCalendar calendarForEntityType:EKEntityTypeReminder eventStore:self.eventStore];
        } else {
            return reject(@"error",
                 [NSString stringWithFormat:@"Calendar entityType %@ is not supported", type],
                 nil);
        }

        calendar.source = calendarSource;
        if (title) {
          calendar.title = title;
        }

        if (color) {
          calendar.CGColor = [RCTConvert UIColor:color].CGColor;
        } else if (options[@"color"] == [NSNull null]) {
          calendar.CGColor = nil;
        }

        NSError *error = nil;
        BOOL success = [self.eventStore saveCalendar:calendar commit:YES error:&error];
        if (success) {
            return resolve(calendar.calendarIdentifier);
        }
        return reject(@"error",
                      [NSString stringWithFormat:@"Calendar %@ could not be saved", title], error);
    }
    @catch (NSException *exception) {
        reject(@"error", @"saveCalendar error", [self exceptionToError:exception]);
    }
}

RCT_EXPORT_METHOD(removeCalendar:(NSString *)calendarId resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    if (![self isCalendarAccessGranted]) {
        reject(@"error", @"unauthorized to access calendar", nil);
        return;
    }


    dispatch_async(serialQueue, ^{
        @try {
            EKCalendar *calendar = (EKCalendar *)[self.eventStore calendarWithIdentifier:calendarId];
            NSError *error = nil;

            BOOL success = [self.eventStore removeCalendar:calendar commit:YES error:&error];
            if (error) {
                return reject(@"error", [error.userInfo valueForKey:@"NSLocalizedDescription"], nil);
            }
            return resolve(@(success));
            }
        @catch (NSException *exception) {
            reject(@"error", @"removeCalendar error", [self exceptionToError:exception]);
        }
    });
}

RCT_EXPORT_METHOD(fetchAllEvents:(NSDate *)startDate endDate:(NSDate *)endDate calendars:(NSArray *)calendars resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    if (![self isCalendarAccessGranted]) {
        reject(@"error", @"unauthorized to access calendar", nil);
        return;
    }

    NSMutableArray *eventCalendars;

    if (calendars.count) {
        eventCalendars = [[NSMutableArray alloc] init];
        NSArray *deviceCalendars = [self.eventStore calendarsForEntityType:EKEntityTypeEvent];

        for (EKCalendar *calendar in deviceCalendars) {
            if ([calendars containsObject:calendar.calendarIdentifier]) {
                [eventCalendars addObject:calendar];
            }
        }
    }

    NSPredicate *predicate = [self.eventStore predicateForEventsWithStartDate:startDate
                                                                      endDate:endDate
                                                                    calendars:eventCalendars];

    __weak RNCalendarEvents *weakSelf = self;
    dispatch_async(serialQueue, ^{
        @try {
            RNCalendarEvents *strongSelf = weakSelf;
            NSArray *calendarEvents = [[strongSelf.eventStore eventsMatchingPredicate:predicate] sortedArrayUsingSelector:@selector(compareStartDateWithEvent:)];
            if (calendarEvents) {
                resolve([strongSelf serializeCalendarEvents:calendarEvents]);
            } else if (calendarEvents == nil) {
                resolve(@[]);
            } else {
                reject(@"error", @"calendar event request error", nil);
            }
        }
        @catch (NSException *exception) {
            reject(@"error", @"fetchAllEvents error", [self exceptionToError:exception]);
        }
    });
}

RCT_EXPORT_METHOD(findEventById:(NSString *)eventId resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    if (![self isCalendarAccessGranted]) {
        reject(@"error", @"unauthorized to access calendar", nil);
        return;
    }

    __weak RNCalendarEvents *weakSelf = self;
    dispatch_async(serialQueue, ^{
        @try {
            RNCalendarEvents *strongSelf = weakSelf;

            EKEvent *calendarEvent = (EKEvent *)[self.eventStore calendarItemWithIdentifier:eventId];
            if (calendarEvent) {
                resolve([strongSelf serializeCalendarEvent:calendarEvent]);
            } else {
                resolve([NSNull null]);
            }
        }
        @catch (NSException *exception) {
            reject(@"error", @"findEventById error", [self exceptionToError:exception]);
        }
    });
}

RCT_EXPORT_METHOD(saveEvent:(NSString *)title
                  settings:(NSDictionary *)settings
                  options:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    if (![self isCalendarAccessGranted]) {
        reject(@"error", @"unauthorized to access calendar", nil);
        return;
    }

    NSMutableDictionary *details = [NSMutableDictionary dictionaryWithDictionary:settings];
    [details setValue:title forKey:_title];

    __weak RNCalendarEvents *weakSelf = self;
    dispatch_async(serialQueue, ^{
        @try {
            RNCalendarEvents *strongSelf = weakSelf;

            NSDictionary *response = [strongSelf buildAndSaveEvent:details options:options];

            if ([response valueForKey:@"success"] != [NSNull null]) {
                resolve([response valueForKey:@"success"]);
            } else {
                reject(@"error", [response valueForKey:@"error"], nil);
            }
        }
        @catch (NSException *exception) {
            reject(@"error", @"saveEvent error", [self exceptionToError:exception]);
        }
    });
}

RCT_EXPORT_METHOD(removeEvent:(NSString *)eventId options:(NSDictionary *)options resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    if (![self isCalendarAccessGranted]) {
        reject(@"error", @"unauthorized to access calendar", nil);
        return;
    }

    Boolean futureEvents = [RCTConvert BOOL:options[@"futureEvents"]];
    NSDate *exceptionDate = [RCTConvert NSDate:options[@"exceptionDate"]];

    if (exceptionDate) {
      NSCalendar *cal = [NSCalendar currentCalendar];
      NSDate *endDate = [cal dateByAddingUnit:NSCalendarUnitDay
                                     value:1
                                    toDate:exceptionDate
                                   options:0];

      NSPredicate *predicate = [self.eventStore predicateForEventsWithStartDate:exceptionDate
                                                                        endDate:endDate
                                                                      calendars:nil];

        __weak RNCalendarEvents *weakSelf = self;
        dispatch_async(serialQueue, ^{
            @try {
                RNCalendarEvents *strongSelf = weakSelf;
                NSArray *calendarEvents = [strongSelf.eventStore eventsMatchingPredicate:predicate];
                EKEvent *eventInstance;
                BOOL success;

                for (EKEvent *event in calendarEvents) {
                    if ([event.calendarItemIdentifier isEqualToString:eventId] && [event.startDate isEqualToDate:exceptionDate]) {
                        eventInstance = event;
                        break;
                    }
                }

                if (eventInstance) {
                    NSError *error = nil;
                    EKSpan eventSpan = EKSpanThisEvent;

                    if (futureEvents) {
                        eventSpan = EKSpanFutureEvents;
                    }

                    success = [strongSelf.eventStore removeEvent:eventInstance span:eventSpan commit:YES error:&error];
                    if (error) {
                        return reject(@"error", [error.userInfo valueForKey:@"NSLocalizedDescription"], nil);
                    }
                } else {
                    return reject(@"error", @"No event found.", nil);
                }

                return resolve(@(success));
            }
            @catch (NSException *exception) {
                reject(@"error", @"removeEvent error", [self exceptionToError:exception]);
            }
        });
    } else {
      __weak RNCalendarEvents *weakSelf = self;
      dispatch_async(serialQueue, ^{
          @try {
              RNCalendarEvents *strongSelf = weakSelf;
              
              EKEvent *calendarEvent = (EKEvent *)[self.eventStore calendarItemWithIdentifier:eventId];
              NSError *error = nil;
              EKSpan eventSpan = EKSpanThisEvent;

              if (futureEvents) {
                  eventSpan = EKSpanFutureEvents;
              }

              BOOL success = [self.eventStore removeEvent:calendarEvent span:eventSpan commit:YES error:&error];
              if (error) {
                  return reject(@"error", [error.userInfo valueForKey:@"NSLocalizedDescription"], nil);
              }
              return resolve(@(success));
              }
          @catch (NSException *exception) {
              reject(@"error", @"removeEvent error", [self exceptionToError:exception]);
          }
      });
    }
}

@end
