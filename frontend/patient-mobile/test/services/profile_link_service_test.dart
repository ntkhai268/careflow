import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/profile_link_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

class LinkApi extends ApiService {
  final calls = <(String, String, dynamic)>[];
  dynamic reply;
  @override
  Future<Response> post(
    String endpoint, {
    dynamic data,
    Options? options,
  }) async {
    calls.add(('POST', endpoint, data));
    return Response(
      requestOptions: RequestOptions(path: endpoint),
      data: {'data': reply},
    );
  }

  @override
  Future<Response> get(
    String endpoint, {
    Map<String, dynamic>? queryParams,
    Options? options,
  }) async {
    calls.add(('GET', endpoint, queryParams));
    return Response(
      requestOptions: RequestOptions(path: endpoint),
      data: {'data': reply},
    );
  }
}

void main() {
  const link = {
    'id': 'request',
    'patientId': 'profile',
    'patientName': 'TEST PERSON',
    'roomId': 'ROOM-01',
    'status': 'PENDING_OWNER',
    'dateOfBirth': '1990-01-01',
  };
  test(
    'authorization sends a stable request and one-time token in body without choosing an owner',
    () async {
      final api = LinkApi()..reply = {'expiresAt': '2026-10-20T06:00:00Z'};
      final service = ProfileLinkService(api);
      final expires = await service.authorize('stable-id', 'test-token');
      await service.authorize('stable-id', 'test-token');
      expect(expires.isUtc, isTrue);
      expect(api.calls[0].$1, 'POST');
      expect(api.calls[0].$2, '/patients/profile-links/authorization');
      expect(api.calls[0].$3, {
        'requestId': 'stable-id',
        'token': 'test-token',
      });
      expect(api.calls[1].$3, api.calls[0].$3);
    },
  );
  test(
    'lists only the authenticated account links and maps pending and duplicate states',
    () async {
      final api = LinkApi()
        ..reply = [
          link,
          {...link, 'id': 'duplicate', 'status': 'DUPLICATE_REVIEW'},
        ];
      final result = await ProfileLinkService(api).mine();
      expect(api.calls.single.$2, '/patients/profile-links/mine');
      expect(result.map((e) => e.status), [
        'PENDING_OWNER',
        'DUPLICATE_REVIEW',
      ]);
      expect(result.first.dateOfBirth, '1990-01-01');
    },
  );
  test(
    'grant and rejection are separate explicit decisions without client owner fields',
    () async {
      final api = LinkApi()..reply = link;
      final service = ProfileLinkService(api);
      await service.decide('request', false);
      await service.decide('request', true);
      expect(api.calls.map((e) => e.$3), [
        {'accepted': false},
        {'accepted': true},
      ]);
      expect(api.calls.every((e) => !e.$2.contains('token')), isTrue);
    },
  );
  test(
    'revocation uses the server link identifier and leaves clinical records intact',
    () async {
      final api = LinkApi();
      await ProfileLinkService(api).revoke('request');
      expect(api.calls.single.$1, 'POST');
      expect(api.calls.single.$2, '/patients/profile-links/request/revoke');
      expect(api.calls.single.$3, isEmpty);
    },
  );
}
