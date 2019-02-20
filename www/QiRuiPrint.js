var exec = require("cordova/exec");

//进行蓝牙扫描
exports.doDiscovery = function(success, error) {
  exec(success, error, "QiRuiPrint", "doDiscovery", []);
};
//终止蓝牙扫描
exports.cancelDiscovery = function(success, error) {
  exec(success, error, "QiRuiPrint", "cancelDiscovery", []);
};
//判断蓝牙开关是否打开
exports.checkBleEnable = function(success, error) {
  exec(success, error, "QiRuiPrint", "checkBleEnable", []);
};
//接收打印机连接状态，若连接成功则返回打印机名称和mac地址
exports.notifyConnectState = function(success, error) {
  exec(success, error, "QiRuiPrint", "notifyConnectState", []);
};
//根据打印机名称和mac地址连接打印机
exports.connectPrinter = function(name, address, success, error) {
  exec(success, error, "QiRuiPrint", "connectPrinter", [name, address]);
};
//让打印机开始打印内容
exports.printContent = function(content, success, error) {
  exec(success, error, "QiRuiPrint", "printContent", [content]);
};
//终止打印服务，断开蓝牙打印机
exports.cancelPrinterServe = function(success, error) {
  exec(success, error, "QiRuiPrint", "cancelPrinterServe", []);
};
