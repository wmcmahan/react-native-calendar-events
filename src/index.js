'use strict'

import { Platform } from 'react-native';

let CalendarEvents;

if (Platform.OS === 'ios') {
  CalendarEvents = require('./index.ios').default;
} else {
  CalendarEvents = require('./index.android').default;
}

export default CalendarEvents;
