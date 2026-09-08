// MCP Diagnostic Caller - calls maker_status and maker_doctor
const { spawn } = require('child_process');

const nodePath = 'C:\\Users\\23911\\AppData\\Roaming\\TRAE SOLO CN\\ModularData\\ai-agent\\vm\\tools\\node\\node.exe';
const makerPath = 'C:\\Users\\23911\\.taptap-maker\\mcp-runtime\\0.0.32\\dist\\maker.js';

const proc = spawn(nodePath, [makerPath], {
  cwd: 'D:\\GameDev',
  env: { ...process.env, TAPTAP_MCP_CLIENT_IDE: 'trae' },
  stdio: ['pipe', 'pipe', 'pipe']
});

let output = '';
let stepCount = 0;
const steps = [
  { name: 'maker_status', tool: 'maker_status', args: {} },
  { name: 'maker_doctor', tool: 'maker_doctor', args: {} },
];

proc.stdout.on('data', (data) => {
  const text = data.toString();
  output += text;
  console.log(text);
});

proc.stderr.on('data', (data) => {
  console.error('STDERR:', data.toString());
});

// Send initialize
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
    jsonrpc: '2.0', id: stepCount + 1, method: 'tools/call',
    params: { name: step.tool, arguments: step.args }
  });
  proc.stdin.write(msg + '\n');
  stepCount++;
  setTimeout(sendNext, 8000);
}

setTimeout(() => {
  const notif = JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' });
  proc.stdin.write(notif + '\n');
  setTimeout(sendNext, 1000);
}, 2000);
