import { describe, expect, it } from "vitest";
import { claveIp } from "../src/ip";

describe("claveIp", () => {
  it("IPv4 tal cual (sin ceros delante)", () => {
    expect(claveIp("203.0.113.7")).toBe("203.0.113.7");
    expect(claveIp(" 203.0.113.7 ")).toBe("203.0.113.7");
    expect(claveIp("010.001.002.003")).toBe("10.1.2.3");
    expect(claveIp("203.0.113.8")).not.toBe(claveIp("203.0.113.7"));
  });

  it("dos IPv6 del mismo /64 dan la misma clave; de otro /64, otra", () => {
    const a = claveIp("2001:db8:1:2::1");
    expect(a).toBe("2001:0db8:0001:0002::/64");
    expect(claveIp("2001:db8:1:2:ffff:eeee:dddd:cccc")).toBe(a);
    expect(claveIp("2001:db8:1:2:0:0:0:abcd")).toBe(a);
    expect(claveIp("2001:db8:1:3::1")).not.toBe(a);
    expect(claveIp("2001:db8:2:2::1")).not.toBe(a);
  });

  it("forma comprimida, expandida y mayúsculas son la misma", () => {
    const a = claveIp("2001:db8::1");
    expect(claveIp("2001:0db8:0000:0000:0000:0000:0000:0001")).toBe(a);
    expect(claveIp("2001:DB8:0:0::1")).toBe(a);
    expect(claveIp("2001:db8::")).toBe(a);
    expect(claveIp("2001:db8:0:0:1::")).toBe(a);
    expect(claveIp("::1")).toBe(claveIp("0:0:0:0:0:0:0:1"));
    expect(claveIp("fe80::1%eth0")).toBe(claveIp("fe80::2")); // con zona
    expect(claveIp("1:2:3:4:5:6:7::")).toBe("0001:0002:0003:0004::/64");
    expect(claveIp("::")).toBe("0000:0000:0000:0000::/64");
  });

  it("IPv4 mapeada en IPv6 cuenta como la IPv4", () => {
    expect(claveIp("::ffff:1.2.3.4")).toBe("1.2.3.4");
    expect(claveIp("::FFFF:1.2.3.4")).toBe("1.2.3.4");
    expect(claveIp("0:0:0:0:0:ffff:1.2.3.4")).toBe("1.2.3.4");
    expect(claveIp("::ffff:0102:0304")).toBe("1.2.3.4");
    expect(claveIp("::ffff:1.2.3.4")).not.toBe(claveIp("::ffff:1.2.3.5"));
  });

  it("sin IP o con algo que no es una IP -> null (la ruta lo manda al cubo fijo sin-ip)", () => {
    expect(claveIp(null)).toBeNull();
    expect(claveIp(undefined)).toBeNull();
    for (const x of [
      "", "  ", "abc", "1.2.3", "1.2.3.256", "1.2.3.4.5", "1.2.3.4%eth0", "1:2:3", "1::2::3", ":::", "12345::",
      "1:2:3:4:5:6:7:8:9", ":1:2:3:4:5:6:7", "::ffff:1.2.3", "1.2.3.4::", "1.2.3.4:1::", "1:2:3:4:5:6:7:8::",
      "1:2:3:4:5:6:7:", "g::1", "2001:db8::1, 10.0.0.1",
    ]) {
      expect(claveIp(x), x).toBeNull();
    }
  });
});
