import { memo, useCallback, useEffect, useRef, useState } from 'react';
import {
  Button,
  SectionList,
  StyleSheet,
  Text,
  View,
  type SectionListRenderItem,
} from 'react-native';
import RNCalendarEvents, {
  type AuthorizationStatus,
  type Calendar,
} from 'react-native-calendar-events';

type LogEntry = { id: number; text: string };
type Row =
  { kind: 'calendar'; calendar: Calendar } | { kind: 'log'; entry: LogEntry };
type Section = { title: string; data: Row[] };

const CalendarRow = memo(function CalendarRowImpl({
  title,
  source,
  color,
  isPrimary,
}: {
  title: string;
  source: string;
  color: string;
  isPrimary: boolean;
}) {
  return (
    <Text style={styles.item}>
      <Text style={{ color }}>●</Text> {title} ({source})
      {isPrimary ? ' · primary' : ''}
    </Text>
  );
});

const LogRow = memo(function LogRowImpl({ text }: { text: string }) {
  return <Text style={styles.log}>{text}</Text>;
});

const renderItem: SectionListRenderItem<Row, Section> = ({ item }) =>
  item.kind === 'calendar' ? (
    <CalendarRow
      title={item.calendar.title}
      source={item.calendar.source}
      color={item.calendar.color}
      isPrimary={item.calendar.isPrimary}
    />
  ) : (
    <LogRow text={item.entry.text} />
  );

const keyExtractor = (item: Row) =>
  item.kind === 'calendar' ? `cal-${item.calendar.id}` : `log-${item.entry.id}`;

const renderSectionHeader = ({ section }: { section: Section }) => (
  <Text style={styles.sectionTitle}>{section.title}</Text>
);

function nextWeek(): [Date, Date] {
  const start = new Date();
  return [start, new Date(start.getTime() + 7 * 24 * 3600 * 1000)];
}

export default function App() {
  const [status, setStatus] = useState<AuthorizationStatus | undefined>();
  const [calendars, setCalendars] = useState<Calendar[]>([]);
  const [log, setLog] = useState<LogEntry[]>([]);
  const nextLogId = useRef(0);

  const append = useCallback((text: string) => {
    const id = nextLogId.current++;
    setLog((prev) => [{ id, text }, ...prev].slice(0, 30));
  }, []);

  const run = useCallback(
    async (label: string, fn: () => Promise<unknown>) => {
      try {
        const result = await fn();
        append(`${label} -> ${JSON.stringify(result)}`);
      } catch (e) {
        append(`${label} failed: ${(e as Error).message}`);
      }
    },
    [append]
  );

  const checkPermissions = useCallback(
    () =>
      run('checkPermissions', async () => {
        const s = await RNCalendarEvents.checkPermissions();
        setStatus(s);
        return s;
      }),
    [run]
  );

  const requestPermissions = useCallback(
    () =>
      run('requestPermissions', async () => {
        const s = await RNCalendarEvents.requestPermissions();
        setStatus(s);
        return s;
      }),
    [run]
  );

  const findCalendars = useCallback(
    () =>
      run('findCalendars', async () => {
        const found = await RNCalendarEvents.findCalendars();
        setCalendars(found);
        return found.length;
      }),
    [run]
  );

  const fetchEvents = useCallback(
    () =>
      run('fetchAllEvents (next 7 days)', async () => {
        const events = await RNCalendarEvents.fetchAllEvents(...nextWeek());
        return events.length;
      }),
    [run]
  );

  // Creates, reads back and removes one event. Only runs when tapped, so the
  // example never writes to the user's calendar on its own.
  const roundTrip = useCallback(
    () =>
      run('save / find / remove round trip', async () => {
        const [start] = nextWeek();
        const id = await RNCalendarEvents.saveEvent('v3 example event', {
          startDate: new Date(start.getTime() + 3600 * 1000),
          endDate: new Date(start.getTime() + 2 * 3600 * 1000),
          notes: 'created by the example app',
          alarms: [{ date: -5 }],
        });
        const saved = await RNCalendarEvents.findEventById(id);
        const removed = await RNCalendarEvents.removeEvent(id);
        return { id, title: saved?.title, removed };
      }),
    [run]
  );

  // Read-only on launch: permissions, calendars, upcoming events.
  useEffect(() => {
    (async () => {
      await checkPermissions();
      await requestPermissions();
      await findCalendars();
      await fetchEvents();
    })();
  }, [checkPermissions, requestPermissions, findCalendars, fetchEvents]);

  const sections: Section[] = [
    {
      title: 'Calendars',
      data: calendars.map((calendar) => ({ kind: 'calendar', calendar })),
    },
    { title: 'Log', data: log.map((entry) => ({ kind: 'log', entry })) },
  ];

  return (
    <SectionList
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={styles.content}
      sections={sections}
      keyExtractor={keyExtractor}
      renderItem={renderItem}
      renderSectionHeader={renderSectionHeader}
      stickySectionHeadersEnabled={false}
      ListHeaderComponent={
        <View style={styles.header}>
          <Text style={styles.title}>react-native-calendar-events v3</Text>
          <Text>Permission: {status ?? 'unknown'}</Text>
          <View style={styles.row}>
            <Button title="Check" onPress={checkPermissions} />
            <Button title="Request" onPress={requestPermissions} />
            <Button title="Calendars" onPress={findCalendars} />
            <Button title="Events" onPress={fetchEvents} />
            <Button title="Round trip" onPress={roundTrip} />
          </View>
        </View>
      }
    />
  );
}

const styles = StyleSheet.create({
  content: { padding: 16, gap: 4 },
  header: { gap: 12, paddingBottom: 8 },
  title: { fontSize: 20, fontWeight: '600' },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  sectionTitle: { fontWeight: '600', paddingTop: 12, paddingBottom: 4 },
  item: { paddingVertical: 4 },
  log: { fontFamily: 'Menlo', fontSize: 11, paddingVertical: 2, opacity: 0.7 },
});
