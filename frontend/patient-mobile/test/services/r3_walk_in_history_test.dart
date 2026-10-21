import 'dart:async';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/walk_in_history_service.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
class HistoryApi extends ApiService {
  final calls=<String>[];bool foreign=false;
  @override Future<Response> get(String endpoint,{Map<String,dynamic>? queryParams,Options? options})async{
    calls.add(endpoint);final data=endpoint.endsWith('/profiles')?[{'id':'profile','userId':'owner','fullName':'SYNTHETIC'}]:endpoint.contains('/consultations/patient/')?[{'id':'walk-in','patientId':foreign?'foreign':'profile','appointmentId':null,'status':'COMPLETED','diagnosis':'SYNTHETIC'},{'id':'booked','patientId':'profile','appointmentId':'appointment','status':'COMPLETED'},{'id':'open','patientId':'profile','status':'IN_PROGRESS'}]:endpoint=='/consultations/walk-in'?{'id':'walk-in','patientId':'profile','status':'COMPLETED'}:endpoint.startsWith('/prescriptions/')?[{'id':'signed','patientId':'profile','consultationId':'walk-in','status':'CONFIRMED'},{'id':'draft','patientId':'profile','consultationId':'walk-in','status':'DRAFT'}]:[{'id':'lab','patientId':'profile','consultationId':'walk-in','status':'REVIEWED'}];
    return Response(requestOptions:RequestOptions(path:endpoint),data:{'data':data});
  }
}
class ScopeAuth extends AuthNotifier {ScopeAuth():super(AuthService(ApiService()));void session(String? owner){state=owner==null?const AuthState(status:AuthStatus.unauthenticated):AuthState(status:AuthStatus.authenticated,userId:owner);}}
class DelayedHistory extends WalkInHistoryService {DelayedHistory():super(ApiService());final old=Completer<List<ClinicalVisit>>();@override Future<List<ClinicalVisit>> list(String owner)=>owner=='a'?old.future:Future.value([const ClinicalVisit(id:'b-visit',patientId:'b-profile',patientName:'B',department:'TEST',diagnosis:'TEST')]);}
void main(){
  TestWidgetsFlutterBinding.ensureInitialized();
  test('completed walk-in history does not invent appointments and includes linked profiles',()async{
    final api=HistoryApi(),service=WalkInHistoryService(HistoryApi());final actual=WalkInHistoryService(api);final rows=await actual.list('owner');expect(rows.map((v)=>v.id),['walk-in']);expect(api.calls,['/patients/user/owner/profiles','/consultations/patient/profile']);
    final result=await service.detail('walk-in');expect(result.prescriptions.map((p)=>p['id']),['signed']);expect(result.labOrders.single['id'],'lab');
  });
  test('foreign profile and consultation bindings fail instead of appearing empty',()async{
    final api=HistoryApi()..foreign=true;await expectLater(WalkInHistoryService(api).list('owner'),throwsFormatException);await expectLater(WalkInHistoryService(HistoryApi()).list('other-account'),throwsFormatException);
  });
  test('account switch discards a late history result and logout clears cached rows',()async{
    final auth=ScopeAuth()..session('a'),service=DelayedHistory();final container=ProviderContainer(overrides:[authProvider.overrideWith((ref)=>auth),walkInHistoryServiceProvider.overrideWithValue(service)]);addTearDown(container.dispose);
    final subscription=container.listen(walkInHistoryProvider,(_,_){});addTearDown(subscription.close);
    final old=container.read(walkInHistoryProvider.future);auth.session('b');final current=await container.read(walkInHistoryProvider.future);expect(current.single.id,'b-visit');
    service.old.complete([const ClinicalVisit(id:'a-secret',patientId:'a-profile',patientName:'A',department:'TEST',diagnosis:'TEST')]);await old;expect(container.read(walkInHistoryProvider).valueOrNull!.single.id,'b-visit');auth.session(null);expect(await container.read(walkInHistoryProvider.future),isEmpty);
  });
}
