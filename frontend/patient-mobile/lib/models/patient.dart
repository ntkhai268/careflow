/// Patient model for CareFlow.
/// Maps to the PatientResponse from the backend API.
class Patient {
  final String id;
  final String userId;
  final String fullName;
  final DateTime? dateOfBirth;
  final String? gender;
  final String? phone;
  final String? idCardNumber;
  final String? insuranceNumber;
  final String? occupation;
  final String? address;
  final String? avatarUrl;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  Patient({
    required this.id,
    required this.userId,
    required this.fullName,
    this.dateOfBirth,
    this.gender,
    this.phone,
    this.idCardNumber,
    this.insuranceNumber,
    this.occupation,
    this.address,
    this.avatarUrl,
    this.createdAt,
    this.updatedAt,
  });

  factory Patient.fromJson(Map<String, dynamic> json) {
    return Patient(
      id: json['id'] as String,
      userId: json['userId'] as String,
      fullName: json['fullName'] as String,
      dateOfBirth: json['dateOfBirth'] != null
          ? DateTime.parse(json['dateOfBirth'] as String)
          : null,
      gender: json['gender'] as String?,
      phone: json['phone'] as String?,
      idCardNumber: json['idCardNumber'] as String?,
      insuranceNumber: json['insuranceNumber'] as String?,
      occupation: json['occupation'] as String?,
      address: json['address'] as String?,
      avatarUrl: json['avatarUrl'] as String?,
      createdAt: json['createdAt'] != null
          ? DateTime.parse(json['createdAt'] as String)
          : null,
      updatedAt: json['updatedAt'] != null
          ? DateTime.parse(json['updatedAt'] as String)
          : null,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'userId': userId,
      'fullName': fullName,
      if (dateOfBirth != null)
        'dateOfBirth':
            '${dateOfBirth!.year}-${dateOfBirth!.month.toString().padLeft(2, '0')}-${dateOfBirth!.day.toString().padLeft(2, '0')}',
      if (gender != null) 'gender': gender,
      if (phone != null) 'phone': phone,
      if (idCardNumber != null) 'idCardNumber': idCardNumber,
      if (insuranceNumber != null) 'insuranceNumber': insuranceNumber,
      if (occupation != null) 'occupation': occupation,
      if (address != null) 'address': address,
      if (avatarUrl != null) 'avatarUrl': avatarUrl,
    };
  }

  /// Generate patient code for display from UUID.
  /// Format: MP-XXXXXXXX (first 8 chars of UUID, uppercase)
  String get patientCode {
    final shortId = id.replaceAll('-', '').substring(0, 10).toUpperCase();
    return 'MP-$shortId';
  }

  /// Formatted gender for display
  String get genderDisplay {
    switch (gender) {
      case 'MALE':
        return 'Nam';
      case 'FEMALE':
        return 'Nữ';
      case 'OTHER':
        return 'Khác';
      default:
        return '';
    }
  }

  Patient copyWith({
    String? fullName,
    DateTime? dateOfBirth,
    String? gender,
    String? phone,
    String? idCardNumber,
    String? insuranceNumber,
    String? occupation,
    String? address,
    String? avatarUrl,
  }) {
    return Patient(
      id: id,
      userId: userId,
      fullName: fullName ?? this.fullName,
      dateOfBirth: dateOfBirth ?? this.dateOfBirth,
      gender: gender ?? this.gender,
      phone: phone ?? this.phone,
      idCardNumber: idCardNumber ?? this.idCardNumber,
      insuranceNumber: insuranceNumber ?? this.insuranceNumber,
      occupation: occupation ?? this.occupation,
      address: address ?? this.address,
      avatarUrl: avatarUrl ?? this.avatarUrl,
      createdAt: createdAt,
      updatedAt: updatedAt,
    );
  }
}
