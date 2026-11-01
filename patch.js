const fs = require('fs');
const file = 'c:/Users/zxcfv/Desktop/study/MessageKeeper/app/src/main/assets/web/assets/index-BJ26Enu0.js';
let code = fs.readFileSync(file, 'utf8');

// Patch 1: Global variables
const patchRegex = /^window\.platformConfig=\{.*?\};\n?/g;
code = code.replace(patchRegex, '');
const prepend = 'window.platformConfig={wechat:{label:\'微信\',color:\'#07C160\'},qq:{label:\'QQ\',color:\'#12B7F5\'},duoshan:{label:\'多闪\',color:\'#FF2A2A\'},qqmusic:{label:\'QQ音乐\',color:\'#FFD700\'},douyin:{label:\'抖音\',color:\'#000000\'}}; window.showUpdateDialog = false; window.setShowUpdateDialog = function(){}; window.showCalendar = false; window.setShowCalendar = function(){};\n';

code = prepend + code;

// Patch 2: Hide Calendar
code = code.replace(/n&&(\(0,\$\.jsx\)\(Xz)/g, 'false&&$1');

fs.writeFileSync(file, code, 'utf8');
console.log('Patched successfully');
