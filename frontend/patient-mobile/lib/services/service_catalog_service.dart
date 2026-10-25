import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'api_service.dart';

class ServicePrice {
  ServicePrice.fromJson(Map<String, dynamic> json)
    : code = json['code'] as String,
      name = json['name'] as String,
      description = json['description'] as String? ?? '',
      amount = (json['amount'] as num).toInt(),
      currency = json['currency'] as String,
      version = (json['version'] as num).toInt();
  final String code, name, description, currency;
  final int amount, version;
}

class ServiceCatalogService {
  ServiceCatalogService(this.api);
  final ApiService api;
  Future<List<ServicePrice>> list() async {
    final response = await api.get('/directory/services');
    return (response.data['data'] as List)
        .map((item) => ServicePrice.fromJson(Map<String, dynamic>.from(item)))
        .toList();
  }
}

final serviceCatalogServiceProvider = Provider(
  (ref) => ServiceCatalogService(ref.watch(apiServiceProvider)),
);
