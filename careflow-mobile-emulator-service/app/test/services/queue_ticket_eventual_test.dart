import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/queue_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

class TicketApiFixture extends ApiService {
  TicketApiFixture(this.failures, this.status);
  final int failures, status;
  int calls = 0;
  @override
  Future<Response> get(String endpoint, {Map<String, dynamic>? queryParams, Options? options}) async {
    calls++;
    final request = RequestOptions(path: endpoint);
    if (calls <= failures) throw DioException.badResponse(statusCode: status, requestOptions: request,
      response: Response(requestOptions: request, statusCode: status));
    return Response(requestOptions: request, data: {'data': {
      'ticketId': 'ticket', 'ticketCode': 'NOI-001', 'appointmentId': 'appointment', 'patientId': 'patient',
      'queueNumber': 'NOI-001', 'department': 'NOI_TONG_QUAT', 'departmentDisplayName': 'Nội tổng quát',
      'roomId': 'ROOM-01', 'roomDisplayName': 'Phòng Nội', 'appointmentDate': '2026-10-16',
      'timeSlot': '10:00-10:30', 'status': 'TICKET_ISSUED',
    }});
  }
}
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('ticket loads from actual response after a brief event propagation 404', () async {
    final api = TicketApiFixture(1, 404);
    final ticket = await QueueService(api).getTicket('appointment');
    expect(ticket.ticketCode, 'NOI-001');
    expect(api.calls, 2);
  });
  test('unauthorized ticket lookup is not hidden by pending-ticket retries', () async {
    final api = TicketApiFixture(1, 401);
    await expectLater(QueueService(api).getTicket('appointment'), throwsA(isA<QueueServiceException>().having((e) => e.statusCode, 'status', 401)));
    expect(api.calls, 1);
  });
  test('a persistently missing ticket stops retrying and retains the truthful pending message', () async {
    final api = TicketApiFixture(10, 404);
    await expectLater(QueueService(api).getTicket('appointment'), throwsA(isA<QueueServiceException>().having((e) => e.message, 'message', contains('đang được hệ thống cấp'))));
    expect(api.calls, 6);
  });
}
