declare namespace Cloudflare {
  interface Env {
    DB: D1Database;
    TEST_MIGRATIONS: import("cloudflare:test").D1Migration[];
    UID_PEPPER: string;
    ADMIN_SECRETO: string;
    LECTOR_SECRETO: string;
    DIAS_MIN_VOTO: string;
    LECTOR_EN_VPS: string;
  }
}
