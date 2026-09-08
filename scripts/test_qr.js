// Try generate_test_qrcode and also read maker://status resource
const { spawn } = require('child_process');

const nodePath = 'C:\\Users\\23911\\AppData\\Roaming\\TRAE SOLO CN\\ModularData\\ai-agent\\vm\\tools\\node\\node.exe';
const makerPath = 'C:\\Users\\23911\\.taptap-maker\\mcp-runtime\\0.0.32\\dist\\maker.js';

const proc = spawn(nodePath, [makerPath], {
  cwd: 'D:\\GameDev',
  env: { ...process.env, TAPTAP_MCP_CLIENT_IDE: 'trae' },
  stdio: ['pipe', 'pipe', 'pipe']
});

let stepCount = 0;
const steps = [
  // Read project status resource
  { name: 'read_status', method: 'resources/read', params: { uri: 'maker://status' } },
  // Try generate test QR code
  { name: 'generate_qr', method: 'tools/call', params: { name: 'generate_test_qrcode', arguments: {} } },
];

proc.stdout.on('data', (data) => console.log(data.toString()));
proc.stderr.on('data', (data) => console.error('STDERR:', data.toString()));

const init = JSON.stringify({
  jsonrpc: '2.0', id: 0, method: 'initialize',
  params: { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'trae', version: '1.0' } }
});
proc.stdin.write(init + '\n');

function sendNext() {
  if (stepCount >= steps.length) {
    setTimeout(() => {
      console.log('\n=== DONE ===');
      proc.kill();
      process.exit(0);
    }, 5000);
    return;
  }
  const step = steps[stepCount];
  console.log('\n=== SENDING: ' + step.name + ' ===');
  const msg = JSON.stringify({
    jsonrpc: '2.0', id: stepCount + 1, method: step.method,
    params: step.params
  });
  proc.stdin.write(msg + '\n');
  stepCount++;
  setTimeout(sendNext, 15000);
}

setTimeout(() => {
  const notif = JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' });
  proc.stdin.write(notif + '\n');
  setTimeout(sendNext, 1000);
}, 2000);
