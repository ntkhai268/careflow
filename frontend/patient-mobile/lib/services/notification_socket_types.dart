abstract interface class NotificationSocket {
  Stream<dynamic> get messages;
  void send(String frame);
  Future<void> close();
}
