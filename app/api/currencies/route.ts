import { route } from '@/lib/http';

const LIST: [string, string, string][] = [
  ['ILS', '₪', 'Israeli new shekel'], ['USD', '$', 'US dollar'], ['EUR', '€', 'Euro'], ['GBP', '£', 'British pound'],
  ['JPY', '¥', 'Japanese yen'], ['CHF', 'CHF', 'Swiss franc'], ['CAD', 'C$', 'Canadian dollar'], ['AUD', 'A$', 'Australian dollar'],
  ['THB', '฿', 'Thai baht'], ['TRY', '₺', 'Turkish lira'], ['GEL', '₾', 'Georgian lari'], ['JOD', 'JD', 'Jordanian dinar'],
  ['EGP', 'E£', 'Egyptian pound'], ['AED', 'AED', 'UAE dirham'], ['CZK', 'Kč', 'Czech koruna'], ['HUF', 'Ft', 'Hungarian forint'],
  ['PLN', 'zł', 'Polish złoty'], ['RON', 'lei', 'Romanian leu'], ['INR', '₹', 'Indian rupee'], ['CNY', '¥', 'Chinese yuan'],
];

export const GET = route(async () => LIST.map(([code, symbol, name]) => ({ code, symbol, name })));
