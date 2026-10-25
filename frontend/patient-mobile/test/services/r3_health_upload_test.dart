import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/health_record_service.dart';
import 'package:careflow_patient/services/medical_file_cache.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

const patient='f0000001-0000-0000-0000-000000000001';
class UploadApi extends ApiService {
  FormData? form;String? path;Completer<void>? paused;bool fail=false;
  @override Future<Response> upload(String endpoint,{required FormData data,required CancelToken cancelToken,void Function(int,int)? onSendProgress})async{
    path=endpoint;form=data;onSendProgress?.call(10,20);await paused?.future;
    if(fail)throw DioException(requestOptions:RequestOptions(path:endpoint),type:DioExceptionType.connectionError);
    return Response(requestOptions:RequestOptions(path:endpoint),data:{'data':{'id':'r3-record','patientId':patient,'recordDate':'2026-10-20','title':'SYNTHETIC','facilityName':'TEST','files':[]}});
  }
}
void main(){
  TestWidgetsFlutterBinding.ensureInitialized();late Directory temp;late UploadApi api;late HealthRecordService service;
  setUp(()async{temp=await Directory.systemTemp.createTemp('careflow-r3-upload-');api=UploadApi();service=HealthRecordService(api);});
  tearDown(()async{await MedicalFileCache.instance.clear();await temp.delete(recursive:true);});
  test('uploads PDF and date-only request as authenticated multipart, with progress',()async{
    final file=await File('${temp.path}/test.pdf').writeAsString('%PDF-1.4\nSYNTHETIC');final progress=<double>[];
    final result=await service.createWithProgress(patient,{'recordDate':'2026-10-20','title':'SYNTHETIC'},[file],cancel:CancelToken(),progress:(sent,total)=>progress.add(sent/total));
    expect(result.patientId,patient);expect(api.path,'/patients/$patient/health-records');expect(progress,[.5]);
    final request=api.form!.files.singleWhere((part)=>part.key=='request').value;
    final bytes=await request.finalize().fold<List<int>>([], (bytes,chunk)=>bytes..addAll(chunk));
    expect(jsonDecode(utf8.decode(bytes))['recordDate'],'2026-10-20');expect(api.form!.files.any((part)=>part.key=='files'),isTrue);
  });
  test('unsupported file and excessive count never start upload',()async{
    final file=await File('${temp.path}/test.docx').writeAsString('SYNTHETIC');
    await expectLater(service.createWithProgress(patient,{},[file],cancel:CancelToken()),throwsFormatException);
    await expectLater(service.createWithProgress(patient,{},List.filled(6,file),cancel:CancelToken()),throwsFormatException);expect(api.form,isNull);
  });
  test('empty and oversized files are rejected before network',()async{
    final empty=await File('${temp.path}/empty.pdf').create();await expectLater(service.createWithProgress(patient,{},[empty],cancel:CancelToken()),throwsFormatException);
    final large=await File('${temp.path}/large.pdf').open(mode:FileMode.write);await large.truncate(10*1024*1024+1);await large.close();
    await expectLater(service.createWithProgress(patient,{},[File('${temp.path}/large.pdf')],cancel:CancelToken()),throwsFormatException);expect(api.form,isNull);
  });
  test('session change cancels an in-flight upload and discards late success',()async{
    api.paused=Completer<void>();final cancel=CancelToken();final pending=service.createWithProgress(patient,{},null,cancel:cancel);
    final assertion=expectLater(pending,throwsFormatException);await Future<void>.delayed(Duration.zero);await MedicalFileCache.instance.clear();expect(cancel.isCancelled,isTrue);api.paused!.complete();await assertion;
  });
  test('user cancellation and connection failure never become success',()async{
    final cancel=CancelToken()..cancel();await expectLater(service.createWithProgress(patient,{},null,cancel:cancel),throwsFormatException);expect(api.form,isNull);
    api.fail=true;await expectLater(service.createWithProgress(patient,{},null,cancel:CancelToken()),throwsA(isA<DioException>()));
  });
}
