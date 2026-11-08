// Coches "realistas" para el reto de verificación: el usuario escribe uno en el campo "coche" de su
// ficha pública y el servidor lo lee. Solo [A-Za-z0-9. ] para que FC lo guarde sin tocarlo.
//
// El espacio tiene que ser GRANDE (Resolución 16, 2026-11-08): con ~440 coches, si el perfil de alguien
// ya mostraba uno de la lista, un atacante podía pedir retos con su uid hasta que saliera ese y
// verificarse sin tocar el perfil (1 de cada ~440). Con >50.000, a ciegas no es viable; y en modo
// Worker `reto` además lee la ficha y nunca da el coche que tiene puesto.
//
// "Marca Modelo Motor Acabado Año", producto de listas pequeñas. Nunca se materializa: `cocheEn(i)`
// lo calcula por aritmética de índices (CPU y memoria mínimas: 10 ms por petición en el plan gratuito).
// Los acabados son de UNA palabra: así el penúltimo token es siempre el acabado y dos combinaciones
// distintas no pueden escribir el mismo texto (el test recorre el espacio entero para comprobarlo).
// Largo máximo 50: el `maxlength` del campo de FC no está medido (en vBulletin suele ser 100).

const ACABADOS: Readonly<Record<string, readonly string[]>> = {
  Seat: ["Reference", "Stella", "Signo", "Sport", "Stylance", "FR", "Style"],
  Volkswagen: ["Trendline", "Comfortline", "Highline", "Sport", "Advance", "Edition", "Sportline"],
  Audi: ["Attraction", "Ambition", "Ambiente", "Sport", "Advance", "Design", "Quattro"],
  BMW: ["Base", "Sport", "Executive", "Exclusive", "Advantage", "Luxury", "Lifestyle"],
  Mercedes: ["Classic", "Elegance", "Avantgarde", "Sport", "Urban", "Style", "Progressive"],
  Renault: ["Authentique", "Expression", "Dynamique", "Privilege", "Initiale", "Emotion", "Limited"],
  Peugeot: ["Access", "Active", "Allure", "Style", "Sport", "Premium", "Griffe"],
  Citroen: ["Attraction", "Seduction", "Exclusive", "Collection", "Premier", "Tendance", "Feel"],
  Ford: ["Ambiente", "Trend", "Ghia", "Titanium", "Sport", "Futura", "Collection"],
  Opel: ["Essentia", "Enjoy", "Cosmo", "Sport", "Club", "Elegance", "Selective"],
  Fiat: ["Active", "Dynamic", "Emotion", "Sport", "Lounge", "Easy", "Pop"],
  Toyota: ["Terra", "Luna", "Sol", "Linea", "Active", "Advance", "Executive"],
  Honda: ["Comfort", "Sport", "Executive", "Elegance", "Lifestyle", "Luxury", "Innova"],
  Nissan: ["Visia", "Acenta", "Tekna", "Comfort", "Sport", "Elegance", "Luxury"],
  Mazda: ["Active", "Sportive", "Luxury", "Style", "Comfort", "Sport", "Zenith"],
  Hyundai: ["GL", "GLS", "Classic", "Comfort", "Style", "Tecno", "Sport"],
  Kia: ["Concept", "Drive", "Emotion", "Active", "Tech", "Sport", "Basic"],
  Mini: ["Pepper", "Chili", "Salt", "Sport", "Classic", "Seven", "Hype"],
  Skoda: ["Active", "Ambiente", "Elegance", "Style", "Sport", "Laurin", "Scout"],
  Volvo: ["Kinetic", "Momentum", "Summum", "Sport", "Base", "Executive", "Classic"],
  "Alfa Romeo": ["Progression", "Distinctive", "Sport", "Exclusive", "Collezione", "Ti", "Elegante"],
  Suzuki: ["GL", "GLX", "JLX", "Sport", "Comfort", "Deluxe", "Club"],
  Dacia: ["Base", "Access", "Ambiance", "Laureate", "Prestige", "Essential", "Comfort"],
  Mitsubishi: ["Inform", "Invite", "Intense", "Kaiteki", "Motion", "Sport", "Instyle"],
  Chevrolet: ["SE", "LS", "LT", "LTZ", "Sport", "Base", "Plus"],
};

// [marca, modelo, motores, primer año, último año]
type Modelo = readonly [string, string, readonly string[], number, number];
const MODELOS: readonly Modelo[] = [
  ["Seat", "Ibiza", ["1.4", "1.4 TDI", "1.9 TDI", "1.9 SDI", "1.6", "1.2 TSI"], 1999, 2017],
  ["Seat", "Leon", ["1.6", "1.9 TDI", "2.0 TDI", "1.8 T", "1.4 TSI", "2.0 TFSI"], 1999, 2018],
  ["Seat", "Cordoba", ["1.4", "1.6", "1.9 TDI", "1.9 SDI", "1.4 TDI"], 1999, 2009],
  ["Seat", "Toledo", ["1.6", "1.9 TDI", "1.8 T", "2.0 TDI", "1.4 TSI"], 1999, 2018],
  ["Seat", "Altea", ["1.6", "1.9 TDI", "2.0 TDI", "1.4 TSI", "1.6 TDI"], 2004, 2015],
  ["Seat", "Arosa", ["1.0", "1.4", "1.7 SDI", "1.4 TDI"], 1997, 2004],
  ["Seat", "Exeo", ["1.6", "2.0 TDI", "1.8 TSI", "2.0 TSI"], 2009, 2013],
  ["Seat", "Alhambra", ["1.9 TDI", "2.0 TDI", "2.8 V6", "1.8 T", "1.4 TSI"], 1996, 2016],
  ["Volkswagen", "Golf", ["1.4", "1.6", "1.9 TDI", "2.0 TDI", "1.8 T", "1.4 TSI"], 1998, 2018],
  ["Volkswagen", "Polo", ["1.2", "1.4", "1.4 TDI", "1.9 TDI", "1.6 TDI", "1.2 TSI"], 1999, 2017],
  ["Volkswagen", "Passat", ["1.6", "1.8 T", "1.9 TDI", "2.0 TDI", "2.0 TSI"], 1997, 2018],
  ["Volkswagen", "Lupo", ["1.0", "1.4", "1.2 TDI", "1.7 SDI", "1.4 TDI"], 1998, 2005],
  ["Volkswagen", "Touran", ["1.6", "1.9 TDI", "2.0 TDI", "1.4 TSI", "1.6 TDI"], 2003, 2018],
  ["Volkswagen", "Caddy", ["1.9 SDI", "1.9 TDI", "2.0 TDI", "1.6 TDI", "1.4"], 2004, 2018],
  ["Volkswagen", "Bora", ["1.6", "1.9 TDI", "2.0", "1.8 T", "2.3 V5"], 1999, 2005],
  ["Volkswagen", "Jetta", ["1.6", "1.9 TDI", "2.0 TDI", "1.4 TSI", "2.0 TFSI"], 2005, 2018],
  ["Volkswagen", "Tiguan", ["1.4 TSI", "2.0 TDI", "2.0 TSI", "1.6 TDI"], 2007, 2018],
  ["Volkswagen", "Scirocco", ["1.4 TSI", "2.0 TSI", "2.0 TDI", "2.0 TFSI"], 2008, 2017],
  ["Volkswagen", "Sharan", ["1.9 TDI", "2.0 TDI", "2.8 V6", "1.8 T", "1.4 TSI"], 1996, 2018],
  ["Volkswagen", "Beetle", ["1.6", "1.9 TDI", "2.0", "1.8 T", "1.2 TSI"], 1998, 2018],
  ["Audi", "A3", ["1.6", "1.9 TDI", "2.0 TDI", "1.8 T", "1.4 TFSI", "2.0 TFSI"], 1996, 2018],
  ["Audi", "A4", ["1.8 T", "1.9 TDI", "2.0 TDI", "2.5 TDI", "2.0 TFSI", "3.0 TDI"], 1995, 2018],
  ["Audi", "A6", ["1.9 TDI", "2.0 TDI", "2.5 TDI", "2.7 TDI", "3.0 TDI", "2.4"], 1997, 2018],
  ["Audi", "TT", ["1.8 T", "3.2 V6", "2.0 TFSI", "2.0 TDI", "2.5 TFSI"], 1998, 2018],
  ["Audi", "A2", ["1.4", "1.4 TDI", "1.6 FSI", "1.2 TDI"], 2000, 2005],
  ["Audi", "A5", ["2.0 TDI", "2.7 TDI", "3.0 TDI", "1.8 TFSI", "2.0 TFSI"], 2007, 2018],
  ["Audi", "Q5", ["2.0 TDI", "3.0 TDI", "2.0 TFSI", "3.2 FSI"], 2008, 2018],
  ["Audi", "Q7", ["3.0 TDI", "4.2 FSI", "4.2 TDI", "3.6 FSI"], 2006, 2018],
  ["Audi", "A1", ["1.2 TFSI", "1.4 TFSI", "1.6 TDI", "1.0 TFSI", "1.4 TDI"], 2010, 2018],
  ["BMW", "Serie 1", ["116i", "118i", "118d", "120d", "120i", "123d"], 2004, 2018],
  ["BMW", "Serie 3", ["316i", "318i", "320i", "318d", "320d", "330d"], 1998, 2018],
  ["BMW", "Serie 5", ["520i", "525i", "520d", "525d", "530d", "535d"], 1996, 2018],
  ["BMW", "X3", ["2.0d", "2.5i", "3.0d", "3.0i", "2.0i"], 2004, 2018],
  ["BMW", "X5", ["3.0d", "3.0i", "4.4i", "4.8i", "3.5d"], 2000, 2018],
  ["BMW", "Z4", ["2.0i", "2.2i", "2.5i", "3.0i", "2.5si"], 2003, 2016],
  ["Mercedes", "Clase A", ["A 140", "A 160", "A 170 CDI", "A 180", "A 200", "A 180 CDI"], 1998, 2018],
  ["Mercedes", "Clase C", ["C 180", "C 200", "C 220 CDI", "C 250 CDI", "C 200 CDI", "C 320 CDI"], 2000, 2018],
  ["Mercedes", "Clase E", ["E 200", "E 220 CDI", "E 270 CDI", "E 280 CDI", "E 320 CDI", "E 350"], 1996, 2018],
  ["Mercedes", "Clase B", ["B 180", "B 200", "B 180 CDI", "B 200 CDI", "B 170"], 2005, 2018],
  ["Mercedes", "Vito", ["108 CDI", "110 CDI", "111 CDI", "115 CDI", "109 CDI"], 1999, 2018],
  ["Mercedes", "CLK", ["CLK 200", "CLK 230", "CLK 270 CDI", "CLK 320", "CLK 220 CDI"], 1997, 2009],
  ["Renault", "Clio", ["1.2", "1.4", "1.5 dCi", "1.9 D", "1.6", "2.0"], 1998, 2018],
  ["Renault", "Megane", ["1.4", "1.6", "1.5 dCi", "1.9 dCi", "2.0", "1.9 D"], 1996, 2018],
  ["Renault", "Laguna", ["1.6", "1.8", "1.9 dCi", "2.2 dCi", "2.0", "2.0 dCi"], 1994, 2015],
  ["Renault", "Twingo", ["1.2", "1.1", "1.5 dCi", "1.2 TCe", "1.6"], 1993, 2018],
  ["Renault", "Scenic", ["1.6", "2.0", "1.9 dCi", "1.5 dCi", "1.4 TCe"], 1996, 2018],
  ["Renault", "Kangoo", ["1.2", "1.4", "1.5 dCi", "1.9 D", "1.6"], 1998, 2018],
  ["Renault", "Kadjar", ["1.2 TCe", "1.5 dCi", "1.6 dCi", "1.3 TCe"], 2015, 2018],
  ["Renault", "Espace", ["2.0", "2.2 dCi", "1.9 dCi", "3.0 dCi", "2.0 dCi"], 1997, 2014],
  ["Peugeot", "206", ["1.1", "1.4", "1.6", "1.4 HDi", "2.0 HDi", "1.6 HDi"], 1998, 2012],
  ["Peugeot", "207", ["1.4", "1.6", "1.4 HDi", "1.6 HDi", "1.6 THP"], 2006, 2014],
  ["Peugeot", "307", ["1.4", "1.6", "2.0", "1.6 HDi", "2.0 HDi"], 2001, 2008],
  ["Peugeot", "308", ["1.6", "1.6 HDi", "2.0 HDi", "1.6 THP", "1.2 PureTech"], 2007, 2018],
  ["Peugeot", "106", ["1.0", "1.1", "1.4", "1.5 D", "1.6"], 1991, 2003],
  ["Peugeot", "406", ["1.8", "2.0", "2.0 HDi", "2.2 HDi", "3.0 V6"], 1995, 2004],
  ["Peugeot", "407", ["1.8", "2.0", "2.0 HDi", "1.6 HDi", "2.2", "2.7 HDi"], 2004, 2011],
  ["Peugeot", "Partner", ["1.6", "1.9 D", "2.0 HDi", "1.6 HDi", "1.4"], 1996, 2018],
  ["Peugeot", "3008", ["1.6", "1.6 HDi", "2.0 HDi", "1.6 THP", "1.2 PureTech"], 2009, 2018],
  ["Citroen", "Saxo", ["1.0", "1.1", "1.4", "1.5 D", "1.6"], 1996, 2004],
  ["Citroen", "C3", ["1.1", "1.4", "1.6", "1.4 HDi", "1.6 HDi"], 2002, 2018],
  ["Citroen", "Xsara", ["1.4", "1.6", "1.9 D", "2.0 HDi", "1.8"], 1997, 2006],
  ["Citroen", "Xsara Picasso", ["1.6", "1.8", "2.0 HDi", "1.6 HDi"], 1999, 2010],
  ["Citroen", "C4", ["1.4", "1.6", "2.0", "1.6 HDi", "2.0 HDi"], 2004, 2018],
  ["Citroen", "Berlingo", ["1.4", "1.6", "1.9 D", "2.0 HDi", "1.6 HDi"], 1996, 2018],
  ["Citroen", "C5", ["1.8", "2.0", "2.0 HDi", "2.2 HDi", "1.6 HDi", "3.0 V6"], 2001, 2017],
  ["Ford", "Fiesta", ["1.25", "1.3", "1.4", "1.6", "1.4 TDCi", "1.6 TDCi"], 1996, 2018],
  ["Ford", "Focus", ["1.4", "1.6", "1.8", "2.0", "1.8 TDCi", "1.6 TDCi"], 1998, 2018],
  ["Ford", "Mondeo", ["1.8", "2.0", "2.0 TDCi", "2.2 TDCi", "2.5 V6", "1.6 TDCi"], 1996, 2018],
  ["Ford", "Ka", ["1.3", "1.2", "1.3 Duratec", "1.6"], 1996, 2016],
  ["Ford", "Fusion", ["1.4", "1.6", "1.4 TDCi", "1.6 TDCi"], 2002, 2012],
  ["Ford", "CMax", ["1.6", "1.8", "2.0", "1.6 TDCi", "2.0 TDCi"], 2003, 2018],
  ["Ford", "Kuga", ["2.0 TDCi", "2.5", "1.6 EcoBoost", "1.5 TDCi"], 2008, 2018],
  ["Opel", "Corsa", ["1.0", "1.2", "1.4", "1.3 CDTI", "1.7 DTI", "1.7 CDTI"], 1993, 2018],
  ["Opel", "Astra", ["1.4", "1.6", "1.8", "1.7 CDTI", "2.0 DTI", "1.9 CDTI"], 1998, 2018],
  ["Opel", "Vectra", ["1.6", "1.8", "2.2", "1.9 CDTI", "2.2 DTI", "2.0 DTI"], 1995, 2008],
  ["Opel", "Zafira", ["1.6", "1.8", "2.2", "1.9 CDTI", "2.0 DTI", "1.7 CDTI"], 1999, 2018],
  ["Opel", "Insignia", ["1.6", "1.8", "2.0 CDTI", "1.4 Turbo", "2.0 Turbo"], 2008, 2017],
  ["Opel", "Meriva", ["1.4", "1.6", "1.3 CDTI", "1.7 CDTI"], 2003, 2017],
  ["Opel", "Combo", ["1.4", "1.6", "1.3 CDTI", "1.7 CDTI", "1.7 DTI"], 2001, 2018],
  ["Fiat", "Punto", ["1.2", "1.4", "1.3 Multijet", "1.9 JTD", "1.9 Multijet", "1.4 TJet"], 1999, 2018],
  ["Fiat", "Panda", ["1.1", "1.2", "1.3 Multijet", "1.4", "0.9 TwinAir"], 2003, 2018],
  ["Fiat", "Bravo", ["1.4", "1.4 TJet", "1.6 Multijet", "1.9 Multijet", "2.0 Multijet"], 2007, 2014],
  ["Fiat", "Stilo", ["1.2", "1.6", "1.8", "1.9 JTD", "2.4"], 2001, 2008],
  ["Fiat", "Doblo", ["1.2", "1.4", "1.9 JTD", "1.3 Multijet", "1.6 Multijet"], 2001, 2018],
  ["Fiat", "500", ["1.2", "1.4", "1.3 Multijet", "0.9 TwinAir", "1.4 TJet"], 2007, 2018],
  ["Toyota", "Corolla", ["1.4", "1.6", "1.8", "1.4 D4D", "2.0 D4D"], 1997, 2018],
  ["Toyota", "Yaris", ["1.0", "1.3", "1.5", "1.4 D4D", "1.5 Hybrid"], 1999, 2018],
  ["Toyota", "Avensis", ["1.6", "1.8", "2.0", "2.0 D4D", "2.2 D4D"], 1997, 2018],
  ["Toyota", "Celica", ["1.8 VVTi", "1.8 TS", "2.0 GT", "1.6"], 1994, 2006],
  ["Toyota", "RAV4", ["2.0", "2.0 D4D", "2.2 D4D", "2.5 Hybrid", "1.8"], 1994, 2018],
  ["Toyota", "Auris", ["1.4", "1.6", "1.4 D4D", "2.0 D4D", "1.8 Hybrid"], 2007, 2018],
  ["Toyota", "Prius", ["1.5 Hybrid", "1.8 Hybrid"], 2000, 2018],
  ["Toyota", "Land Cruiser", ["3.0 D4D", "4.0 V6", "2.8 D4D", "4.2 TD"], 1996, 2018],
  ["Honda", "Civic", ["1.4", "1.6", "1.8", "2.0 Type R", "2.2 iCTDi", "1.6 VTEC"], 1995, 2018],
  ["Honda", "Accord", ["1.8", "2.0", "2.4", "2.2 iCTDi", "2.2 iDTEC"], 1998, 2015],
  ["Honda", "CRV", ["2.0", "2.4", "2.2 iCTDi", "2.2 iDTEC", "1.6 iDTEC"], 1997, 2018],
  ["Honda", "Jazz", ["1.2", "1.4", "1.3", "1.5"], 2002, 2018],
  ["Honda", "Prelude", ["2.0", "2.2 VTi", "2.2 VTEC"], 1992, 2001],
  ["Honda", "HRV", ["1.6", "1.5", "1.6 iDTEC"], 1999, 2018],
  ["Nissan", "Micra", ["1.0", "1.2", "1.4", "1.5 dCi", "1.6"], 1993, 2018],
  ["Nissan", "Almera", ["1.5", "1.8", "2.2 dCi", "1.5 dCi", "2.2 Di"], 1995, 2006],
  ["Nissan", "Primera", ["1.6", "1.8", "2.0", "1.9 dCi", "2.2 dCi"], 1996, 2008],
  ["Nissan", "Qashqai", ["1.6", "2.0", "1.5 dCi", "1.6 dCi", "1.2 DIGT"], 2007, 2018],
  ["Nissan", "Note", ["1.4", "1.6", "1.5 dCi", "1.2 DIGS"], 2006, 2018],
  ["Nissan", "Patrol", ["3.0 Di", "2.8 TD", "4.2 TD", "4.8"], 1998, 2010],
  ["Nissan", "XTrail", ["2.0", "2.5", "2.2 dCi", "2.0 dCi", "1.6 dCi"], 2001, 2018],
  ["Mazda", "MX5", ["1.6", "1.8", "2.0", "1.5"], 1998, 2018],
  ["Mazda", "Mazda3", ["1.6", "2.0", "1.6 CRTD", "2.0 CRTD", "2.2 CRTD"], 2003, 2018],
  ["Mazda", "Mazda6", ["1.8", "2.0", "2.3", "2.0 CRTD", "2.2 CRTD"], 2002, 2018],
  ["Mazda", "Mazda2", ["1.3", "1.5", "1.4 CRTD", "1.6 CRTD"], 2003, 2018],
  ["Mazda", "CX5", ["2.0", "2.5", "2.2 D"], 2012, 2018],
  ["Hyundai", "Accent", ["1.3", "1.5", "1.6", "1.5 CRDi"], 1994, 2012],
  ["Hyundai", "Coupe", ["1.6", "2.0", "2.7 V6", "2.0 FX"], 1996, 2009],
  ["Hyundai", "Getz", ["1.1", "1.3", "1.4", "1.5 CRDi"], 2002, 2011],
  ["Hyundai", "i30", ["1.4", "1.6", "1.6 CRDi", "2.0 CRDi", "1.4 CRDi"], 2007, 2018],
  ["Hyundai", "Tucson", ["2.0", "2.7 V6", "2.0 CRDi", "1.7 CRDi", "1.6 TGDI"], 2004, 2018],
  ["Hyundai", "Santa Fe", ["2.0 CRDi", "2.2 CRDi", "2.7 V6", "2.4"], 2000, 2018],
  ["Hyundai", "Matrix", ["1.5 CRDi", "1.6", "1.8"], 2001, 2010],
  ["Kia", "Rio", ["1.3", "1.4", "1.5 CRDi", "1.6", "1.1 CRDi"], 2000, 2018],
  ["Kia", "Ceed", ["1.4", "1.6", "1.6 CRDi", "2.0", "1.0 TGDI"], 2006, 2018],
  ["Kia", "Sportage", ["2.0", "2.0 CRDi", "1.7 CRDi", "1.6 GDI", "2.7 V6"], 1994, 2018],
  ["Kia", "Picanto", ["1.0", "1.1", "1.2", "1.1 CRDi"], 2004, 2018],
  ["Kia", "Carens", ["1.6", "1.8", "2.0 CRDi", "1.7 CRDi"], 2000, 2018],
  ["Kia", "Sorento", ["2.5 CRDi", "2.2 CRDi", "3.5 V6", "2.4"], 2002, 2018],
  ["Mini", "Cooper", ["1.6", "1.6 S", "1.4 D", "1.6 D", "2.0 D"], 2001, 2018],
  ["Mini", "One", ["1.4", "1.6", "1.4 D", "1.6 D"], 2001, 2018],
  ["Mini", "Countryman", ["1.6", "1.6 D", "2.0 D", "1.6 S"], 2010, 2018],
  ["Skoda", "Octavia", ["1.4", "1.6", "1.8 T", "1.9 TDI", "2.0 TDI", "1.4 TSI"], 1997, 2018],
  ["Skoda", "Fabia", ["1.2", "1.4", "1.4 TDI", "1.9 TDI", "1.6 TDI", "1.2 TSI"], 1999, 2018],
  ["Skoda", "Superb", ["1.8 T", "1.9 TDI", "2.0 TDI", "2.5 TDI", "1.4 TSI"], 2001, 2018],
  ["Skoda", "Roomster", ["1.2", "1.4", "1.6", "1.4 TDI", "1.9 TDI", "1.6 TDI"], 2006, 2015],
  ["Skoda", "Yeti", ["1.2 TSI", "1.4 TSI", "1.8 TSI", "2.0 TDI", "1.6 TDI"], 2009, 2017],
  ["Volvo", "S40", ["1.6", "1.8", "2.0", "1.9 D", "2.0 D", "2.4"], 1996, 2012],
  ["Volvo", "V70", ["2.4", "2.5 T", "2.4 D5", "2.4 D", "2.0 T"], 1997, 2016],
  ["Volvo", "S60", ["2.0 T", "2.4", "2.4 D5", "2.4 D", "2.5 T"], 2000, 2018],
  ["Volvo", "XC90", ["2.4 D5", "2.5 T", "2.9 T6", "3.2", "4.4 V8"], 2002, 2018],
  ["Volvo", "V50", ["1.6", "1.8", "2.0 D", "1.6 D", "2.4"], 2004, 2012],
  ["Alfa Romeo", "147", ["1.6 TS", "2.0 TS", "1.9 JTD", "1.9 JTDm", "3.2 V6"], 2000, 2010],
  ["Alfa Romeo", "156", ["1.6 TS", "1.8 TS", "2.0 TS", "1.9 JTD", "2.4 JTD", "2.5 V6"], 1997, 2007],
  ["Alfa Romeo", "159", ["1.8", "2.2", "1.9 JTDm", "2.4 JTDm", "3.2 V6"], 2005, 2011],
  ["Alfa Romeo", "GT", ["1.8 TS", "2.0 JTS", "1.9 JTDm", "3.2 V6"], 2003, 2010],
  ["Alfa Romeo", "Giulietta", ["1.4 TB", "1.6 JTDm", "2.0 JTDm", "1.75 TBi"], 2010, 2018],
  ["Alfa Romeo", "MiTo", ["1.4", "1.4 TB", "1.3 JTDm", "1.6 JTDm"], 2008, 2018],
  ["Suzuki", "Swift", ["1.3", "1.5", "1.6", "1.3 DDiS", "1.2"], 1996, 2018],
  ["Suzuki", "Vitara", ["1.6", "2.0", "2.0 TD", "1.9 DDiS", "1.6 DDiS"], 1995, 2018],
  ["Suzuki", "Jimny", ["1.3", "1.5 DDiS", "1.3 VVT"], 1998, 2018],
  ["Suzuki", "SX4", ["1.5", "1.6", "1.9 DDiS", "2.0 DDiS"], 2006, 2014],
  ["Dacia", "Sandero", ["1.2", "1.4", "1.6", "1.5 dCi", "0.9 TCe"], 2008, 2018],
  ["Dacia", "Logan", ["1.4", "1.6", "1.5 dCi", "1.2", "0.9 TCe"], 2004, 2018],
  ["Dacia", "Duster", ["1.6", "1.5 dCi", "1.2 TCe", "1.3 TCe"], 2010, 2018],
  ["Mitsubishi", "Lancer", ["1.3", "1.6", "2.0", "1.8 DID", "2.0 DID"], 1996, 2016],
  ["Mitsubishi", "Montero", ["2.5 TD", "3.2 DID", "3.5 V6", "3.8 V6"], 1991, 2018],
  ["Mitsubishi", "Outlander", ["2.0", "2.4", "2.0 DID", "2.2 DID"], 2003, 2018],
  ["Mitsubishi", "ASX", ["1.6", "1.8 DID", "2.0", "1.6 DID"], 2010, 2018],
  ["Mitsubishi", "Colt", ["1.1", "1.3", "1.5", "1.5 DID"], 1996, 2012],
  ["Chevrolet", "Aveo", ["1.2", "1.4", "1.6", "1.3 VCDi"], 2005, 2015],
  ["Chevrolet", "Cruze", ["1.6", "1.8", "2.0 VCDi", "1.7 VCDi"], 2009, 2015],
  ["Chevrolet", "Captiva", ["2.0 VCDi", "2.4", "2.2 VCDi", "3.2 V6"], 2006, 2015],
  ["Chevrolet", "Kalos", ["1.2", "1.4"], 2005, 2008],
];

// Dónde empieza cada modelo en la numeración global (unos 150 números: lo único que se precalcula).
const INICIOS: number[] = [];
let total = 0;
for (const [marca, , motores, desde, hasta] of MODELOS) {
  INICIOS.push(total);
  total += motores.length * ACABADOS[marca].length * (hasta - desde + 1);
}
const TOTAL = total;

/** Cuántos coches distintos hay. */
export function totalCoches(): number {
  return TOTAL;
}

/** El coche número `i` (0 ≤ i < totalCoches()), sin construir la lista. */
export function cocheEn(i: number): string {
  if (!Number.isInteger(i) || i < 0 || i >= TOTAL) throw new RangeError(`Índice de coche fuera de rango: ${i}`);
  // Último modelo cuyo inicio es ≤ i (búsqueda binaria).
  let lo = 0;
  let hi = MODELOS.length - 1;
  while (lo < hi) {
    const medio = (lo + hi + 1) >> 1;
    if (INICIOS[medio] <= i) lo = medio;
    else hi = medio - 1;
  }
  const [marca, modelo, motores, desde, hasta] = MODELOS[lo];
  const acabados = ACABADOS[marca];
  const anios = hasta - desde + 1;
  let r = i - INICIOS[lo];
  const anio = desde + (r % anios);
  r = Math.floor(r / anios);
  const acabado = acabados[r % acabados.length];
  const motor = motores[Math.floor(r / acabados.length)];
  return `${marca} ${modelo} ${motor} ${acabado} ${anio}`;
}

function normalizar(s: string): string {
  return s.trim().replace(/\s+/g, " ").toLowerCase();
}

/** Mismo coche sin mirar mayúsculas ni espacios de más (FC puede recolocarlos al guardar). */
export function cochesIguales(a: string, b: string): boolean {
  return normalizar(a) === normalizar(b);
}

/**
 * Un coche al azar (`aleatorio()` en [0,1)) que nunca es `distintoDe` (el que ya tiene el perfil, o
 * el del reto anterior). Como no hay dos coches iguales en el espacio, basta con pasar al siguiente.
 */
export function cocheAleatorio(aleatorio: () => number, distintoDe: string | null): string {
  let i = Math.min(TOTAL - 1, Math.floor(aleatorio() * TOTAL));
  if (distintoDe !== null && cochesIguales(cocheEn(i), distintoDe)) i = (i + 1) % TOTAL;
  return cocheEn(i);
}
