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
      id: json['id'] ?? '',
      fileName: json['fileName'] ?? '',
      fileUrl: json['fileUrl'] ?? '',
      fileSize: json['fileSize'],
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

  factory HealthRecord.fromJson(Map<String, dynamic> json) {
    return HealthRecord(
      id: json['id'] ?? '',
      patientId: json['patientId'] ?? '',
      title: json['title'] ?? '',
      recordDate: json['recordDate'] != null ? DateTime.parse(json['recordDate']) : DateTime.now(),
      facilityName: json['facilityName'] ?? '',
      notes: json['notes'],
      bloodSugar: json['bloodSugar']?.toDouble(),
      bloodPressure: json['bloodPressure'],
      heightCm: json['heightCm']?.toDouble(),
      weightKg: json['weightKg']?.toDouble(),
      bmi: json['bmi']?.toDouble(),
      waistCm: json['waistCm']?.toDouble(),
      bloodType: json['bloodType'],
      pulse: json['pulse']?.toInt(),
      temperature: json['temperature']?.toDouble(),
      respiratoryRate: json['respiratoryRate']?.toInt(),
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
