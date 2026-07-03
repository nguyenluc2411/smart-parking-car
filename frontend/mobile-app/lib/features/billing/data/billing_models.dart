// Models khớp docs/api-contracts.md (billing-service :8082).

class Invoice {
  final String invoiceId;
  final String sessionId;
  final String plateNumber;
  final String entryTime;
  final String? exitTime;
  final int durationMinutes;
  final double ratePerMin;
  final bool peakApplied;
  final bool overnightApplied;
  final num amount;
  final String status; // PENDING | PAID

  const Invoice({
    required this.invoiceId,
    required this.sessionId,
    required this.plateNumber,
    required this.entryTime,
    required this.exitTime,
    required this.durationMinutes,
    required this.ratePerMin,
    required this.peakApplied,
    required this.overnightApplied,
    required this.amount,
    required this.status,
  });

  factory Invoice.fromJson(Map<String, dynamic> j) => Invoice(
        invoiceId: j['invoiceId'] as String,
        sessionId: j['sessionId'] as String,
        plateNumber: j['plateNumber'] as String,
        entryTime: j['entryTime'] as String,
        exitTime: j['exitTime'] as String?,
        durationMinutes: j['durationMinutes'] as int,
        ratePerMin: (j['ratePerMin'] as num).toDouble(),
        peakApplied: j['peakApplied'] as bool,
        overnightApplied: j['overnightApplied'] as bool,
        amount: j['amount'] as num,
        status: j['status'] as String,
      );
}

class PaymentResult {
  final String invoiceId;
  final String status;
  final String? paidAt;

  const PaymentResult({
    required this.invoiceId,
    required this.status,
    required this.paidAt,
  });

  factory PaymentResult.fromJson(Map<String, dynamic> j) => PaymentResult(
        invoiceId: j['invoiceId'] as String,
        status: j['status'] as String,
        paidAt: j['paidAt'] as String?,
      );
}

class RateSchedule {
  final int hourStart;
  final int hourEnd;
  final bool isPeak;
  final String dayType;

  const RateSchedule({
    required this.hourStart,
    required this.hourEnd,
    required this.isPeak,
    required this.dayType,
  });

  factory RateSchedule.fromJson(Map<String, dynamic> j) => RateSchedule(
        hourStart: j['hourStart'] as int,
        hourEnd: j['hourEnd'] as int,
        isPeak: j['isPeak'] as bool,
        dayType: j['dayType'] as String,
      );
}

class Rate {
  final String id;
  final double ratePerMin;
  final double peakMultiplier;
  final num overnightFlat;
  final num minCharge;
  final String effectiveFrom;
  final String? effectiveTo;
  final List<RateSchedule> schedules;

  const Rate({
    required this.id,
    required this.ratePerMin,
    required this.peakMultiplier,
    required this.overnightFlat,
    required this.minCharge,
    required this.effectiveFrom,
    required this.effectiveTo,
    required this.schedules,
  });

  factory Rate.fromJson(Map<String, dynamic> j) => Rate(
        id: j['id'] as String,
        ratePerMin: (j['ratePerMin'] as num).toDouble(),
        peakMultiplier: (j['peakMultiplier'] as num).toDouble(),
        overnightFlat: j['overnightFlat'] as num,
        minCharge: j['minCharge'] as num,
        effectiveFrom: j['effectiveFrom'] as String,
        effectiveTo: j['effectiveTo'] as String?,
        schedules: (j['schedules'] as List<dynamic>? ?? const [])
            .map((e) => RateSchedule.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
      );
}

class HourRevenue {
  final int hour;
  final num revenue;
  final int sessions;
  const HourRevenue(
      {required this.hour, required this.revenue, required this.sessions});

  factory HourRevenue.fromJson(Map<String, dynamic> j) => HourRevenue(
        hour: j['hour'] as int,
        revenue: j['revenue'] as num,
        sessions: j['sessions'] as int,
      );
}

class DailyReport {
  final String date;
  final int totalSessions;
  final num totalRevenue;
  final int peakSessions;
  final int avgDurationMinutes;
  final List<HourRevenue> revenueByHour;

  const DailyReport({
    required this.date,
    required this.totalSessions,
    required this.totalRevenue,
    required this.peakSessions,
    required this.avgDurationMinutes,
    required this.revenueByHour,
  });

  factory DailyReport.fromJson(Map<String, dynamic> j) => DailyReport(
        date: j['date'] as String,
        totalSessions: j['totalSessions'] as int,
        totalRevenue: j['totalRevenue'] as num,
        peakSessions: j['peakSessions'] as int,
        avgDurationMinutes: j['avgDurationMinutes'] as int,
        revenueByHour: (j['revenueByHour'] as List<dynamic>? ?? const [])
            .map((e) => HourRevenue.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
      );
}

class DayRevenue {
  final String date;
  final num revenue;
  const DayRevenue({required this.date, required this.revenue});

  factory DayRevenue.fromJson(Map<String, dynamic> j) => DayRevenue(
        date: j['date'] as String,
        revenue: j['revenue'] as num,
      );
}

class MonthlyReport {
  final String month;
  final int totalSessions;
  final num totalRevenue;
  final num prevMonthRevenue;
  final double growthRate;
  final num avgDailyRevenue;
  final List<DayRevenue> revenueByDay;

  const MonthlyReport({
    required this.month,
    required this.totalSessions,
    required this.totalRevenue,
    required this.prevMonthRevenue,
    required this.growthRate,
    required this.avgDailyRevenue,
    required this.revenueByDay,
  });

  factory MonthlyReport.fromJson(Map<String, dynamic> j) => MonthlyReport(
        month: j['month'] as String,
        totalSessions: j['totalSessions'] as int,
        totalRevenue: j['totalRevenue'] as num,
        prevMonthRevenue: j['prevMonthRevenue'] as num,
        growthRate: (j['growthRate'] as num).toDouble(),
        avgDailyRevenue: j['avgDailyRevenue'] as num,
        revenueByDay: (j['revenueByDay'] as List<dynamic>? ?? const [])
            .map((e) => DayRevenue.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
      );
}
