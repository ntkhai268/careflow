class HealthRecordFileInfo {
  final String id;
  final String fileName;
  final String fileUrl;
  final int? fileSize;
  final String? contentType;

  HealthRecordFileInfo({
    required this.id,
    required this.fileName,
    required this.fileUrl,
    this.fileSize,
    this.contentType,
  });

  factory HealthRecordFileInfo.fromJson(Map<String, dynamic> json) {
    return HealthRecordFileInfo(
      id: json['id']?.toString() ?? '',
      fileName: json['fileName'] ?? '',
      fileUrl: json['fileUrl'] ?? '',
      fileSize: json['fileSize'] is int
          ? json['fileSize']
          : (json['fileSize'] is String ? int.tryParse(json['fileSize']) : json['fileSize']?.toInt()),
      contentType: json['contentType'],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'fileName': fileName,
      'fileUrl': fileUrl,
      'fileSize': fileSize,
      'contentType': contentType,
    };
  }
}

class HealthRecord {
  final String id;
  final String patientId;
  final String title;
  final DateTime recordDate;
  final String facilityName;
  final String? notes;
  final double? bloodSugar;
  final String? bloodPressure;
  final double? heightCm;
  final double? weightKg;
  final double? bmi;
  final double? waistCm;
  final String? bloodType;
  final int? pulse;
  final double? temperature;
  final int? respiratoryRate;
  
  final String? drugAllergy;
  final String? chemicalAllergy;
  final String? foodAllergy;
  
  final String? heartDisease;
  final String? hypertension;
  final String? mentalIllness;
  final String? cancer;
  final String? asthma;
  final String? epilepsy;
  final String? tuberculosis;

  final DateTime? createdAt;
  final DateTime? updatedAt;
  final List<HealthRecordFileInfo>? files;

  HealthRecord({
    required this.id,
    required this.patientId,
    required this.title,
    required this.recordDate,
    required this.facilityName,
    this.notes,
    this.bloodSugar,
    this.bloodPressure,
    this.heightCm,
    this.weightKg,
    this.bmi,
    this.waistCm,
    this.bloodType,
    this.pulse,
    this.temperature,
    this.respiratoryRate,
    this.drugAllergy,
    this.chemicalAllergy,
    this.foodAllergy,
    this.heartDisease,
    this.hypertension,
    this.mentalIllness,
    this.cancer,
    this.asthma,
    this.epilepsy,
    this.tuberculosis,
    this.createdAt,
    this.updatedAt,
    this.files,
  });

  static int? _toInt(dynamic v) {
    if (v == null) return null;
    if (v is int) return v;
    if (v is num) return v.toInt();
    if (v is String) return int.tryParse(v);
    return null;
  }

  static double? _toDouble(dynamic v) {
    if (v == null) return null;
    if (v is double) return v;
    if (v is num) return v.toDouble();
    if (v is String) return double.tryParse(v);
    return null;
  }

  factory HealthRecord.fromJson(Map<String, dynamic> json) {
    return HealthRecord(
      id: json['id']?.toString() ?? '',
      patientId: json['patientId']?.toString() ?? '',
      title: json['title'] ?? '',
      recordDate: json['recordDate'] != null ? DateTime.parse(json['recordDate']) : DateTime.now(),
      facilityName: json['facilityName'] ?? '',
      notes: json['notes'],
      bloodSugar: _toDouble(json['bloodSugar']),
      bloodPressure: json['bloodPressure'],
      heightCm: _toDouble(json['heightCm']),
      weightKg: _toDouble(json['weightKg']),
      bmi: _toDouble(json['bmi']),
      waistCm: _toDouble(json['waistCm']),
      bloodType: json['bloodType'],
      pulse: _toInt(json['pulse']),
      temperature: _toDouble(json['temperature']),
      respiratoryRate: _toInt(json['respiratoryRate']),
      drugAllergy: json['drugAllergy'],
      chemicalAllergy: json['chemicalAllergy'],
      foodAllergy: json['foodAllergy'],
      heartDisease: json['heartDisease'],
      hypertension: json['hypertension'],
      mentalIllness: json['mentalIllness'],
      cancer: json['cancer'],
      asthma: json['asthma'],
      epilepsy: json['epilepsy'],
      tuberculosis: json['tuberculosis'],
      createdAt: json['createdAt'] != null ? DateTime.parse(json['createdAt']) : null,
      updatedAt: json['updatedAt'] != null ? DateTime.parse(json['updatedAt']) : null,
      files: json['files'] != null ? (json['files'] as List).map((i) => HealthRecordFileInfo.fromJson(i)).toList() : null,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'patientId': patientId,
      'title': title,
      'recordDate': recordDate.toIso8601String(),
      'facilityName': facilityName,
      'notes': notes,
      'bloodSugar': bloodSugar,
      'bloodPressure': bloodPressure,
      'heightCm': heightCm,
      'weightKg': weightKg,
      'bmi': bmi,
      'waistCm': waistCm,
      'bloodType': bloodType,
      'pulse': pulse,
      'temperature': temperature,
      'respiratoryRate': respiratoryRate,
      'drugAllergy': drugAllergy,
      'chemicalAllergy': chemicalAllergy,
      'foodAllergy': foodAllergy,
      'heartDisease': heartDisease,
      'hypertension': hypertension,
      'mentalIllness': mentalIllness,
      'cancer': cancer,
      'asthma': asthma,
      'epilepsy': epilepsy,
      'tuberculosis': tuberculosis,
      'createdAt': createdAt?.toIso8601String(),
      'updatedAt': updatedAt?.toIso8601String(),
      'files': files?.map((e) => e.toJson()).toList(),
    };
  }
}
