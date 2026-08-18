// استبدل كود الـ Button داخل HomeScreen بهذا الكود:
Button(
    onClick = {
        if (smsPermissionGranted) {
            viewModel.importFromInbox()
        } else {
            onRequestSmsPermission()
        }
    },
    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
) {
    Text("مزامنة / استيراد", color = Color.White)
}