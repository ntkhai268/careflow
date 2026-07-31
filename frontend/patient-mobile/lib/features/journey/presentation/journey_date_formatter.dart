import 'package:intl/intl.dart';

const _bangkokOffset = Duration(hours: 7);

/// Formats persisted journey instants for CareFlow's Asia/Bangkok hospital time.
/// Bangkok stays at UTC+7 throughout the year and does not observe daylight saving.
String formatJourneyDateTime(DateTime instant, String pattern) =>
    DateFormat(pattern).format(instant.toUtc().add(_bangkokOffset));
