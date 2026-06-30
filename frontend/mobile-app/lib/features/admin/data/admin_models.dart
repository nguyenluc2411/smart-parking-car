// Models khớp docs/api-contracts.md (admin-service :8083).

class AuthResult {
  final String accessToken;
  final String refreshToken;
  final int expiresIn;
  final String role; // ADMIN | OPERATOR

  const AuthResult({
    required this.accessToken,
    required this.refreshToken,
    required this.expiresIn,
    required this.role,
  });

  factory AuthResult.fromJson(Map<String, dynamic> j) => AuthResult(
        accessToken: j['accessToken'] as String,
        refreshToken: j['refreshToken'] as String,
        expiresIn: j['expiresIn'] as int,
        role: j['role'] as String,
      );
}

class AppUser {
  final String id;
  final String username;
  final String email;
  final String role;
  final bool isActive;
  final String createdAt;

  const AppUser({
    required this.id,
    required this.username,
    required this.email,
    required this.role,
    required this.isActive,
    required this.createdAt,
  });

  factory AppUser.fromJson(Map<String, dynamic> j) => AppUser(
        id: j['id'] as String,
        username: j['username'] as String,
        email: j['email'] as String,
        role: j['role'] as String,
        isActive: j['isActive'] as bool,
        createdAt: j['createdAt'] as String,
      );
}
