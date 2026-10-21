import 'package:careflow_patient/services/local_document_reader.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('QR extracts only the three intended fields', () {
    final fields = DocumentFields.parse(
      '',
      qr: '000000000001|123456789|TEST PERSON|02011990|Nam|PRIVATE ADDRESS|01012020',
    );
    expect(fields.number, '000000000001');
    expect(fields.name, 'TEST PERSON');
    expect(fields.birth, '02/01/1990');
    expect(DocumentFields.isoBirth(fields.birth), '1990-01-02');
  });
  test(
    'OCR uses document labels and leaves missing information for user review',
    () {
      final fields = DocumentFields.parse(
        '000000000001\nHọ và tên / Full name:\nTEST PERSON\nNgày sinh / Date of birth: 02/01/1990',
      );
      expect(fields.number, '000000000001');
      expect(fields.name, 'TEST PERSON');
      expect(fields.birth, '02/01/1990');
      expect(DocumentFields.parse('unreadable').name, isEmpty);
      expect(DocumentFields.parse('10000000000001').number, isEmpty);
    },
  );
  test('invalid, future and malformed birth dates never become API values', () {
    for (final value in [
      '31/02/1990',
      '01/01/3000',
      '01/01/1800',
      '2020-01-01',
      '',
    ]) {
      expect(DocumentFields.isoBirth(value), isNull);
    }
  });
  test(
    'new card labels, spaced digits and printed date separators are normalized',
    () {
      final fields = DocumentFields.parse(
        '000 000 000 001\nHọ, chữ đệm và tên khai sinh / Fullname: TEST PERSON\nDate of birth: 02 - 01 - 1990',
      );
      expect(fields.number, '000000000001');
      expect(fields.name, 'TEST PERSON');
      expect(fields.birth, '02/01/1990');
    },
  );
  test('bilingual inline labels do not replace the extracted name', () {
    final fields = DocumentFields.parse(
      'Ho va ten: / Full name: TEST PERSON\nDate of birth: 02 . 01 . 1990',
    );
    expect(fields.name, 'TEST PERSON');
    expect(fields.birth, '02/01/1990');
  });
}
