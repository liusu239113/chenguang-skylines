// MCP Build Caller - sends JSON-RPC messages to Maker MCP server
const { spawn } = require('child_process');
const path = require('path');

const nodePath = 'C:\\Users\\23911\\AppData\\Roaming\\TRAE SOLO CN\\ModularData\\ai-agent\\vm\\tools\\node\\node.exe';
const makerPath = 'C:\\Users\\23911\\.taptap-maker\\mcp-runtime\\0.0.32\\dist\\maker.js';

const proc = spawn(nodePath, [makerPath], {
  cwd: 'D:\\GameDev',
  env: { ...process.env, TAPTAP_MCP_CLIENT_IDE: 'trae' },
  stdio: ['pipe', 'pipe', 'pipe']
});

let output = '';

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

// Wait then send notifications/initialized
setTimeout(() => {
  const notif = JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' });
  proc.stdin.write(notif + '\n');

  // Wait then send build call
  setTimeout(() => {
    const build = JSON.stringify({
      jsonrpc: '2.0', id: 1, method: 'tools/call',
      params: { name: 'maker_build_current_directory', arguments: { target_dir: 'D:\\GameDev' } }
    });
    proc.stdin.write(build + '\n');
  }, 1000);
}, 2000);

// Wait for response
setTimeout(() => {
  console.log('\n=== WAITING FOR BUILD RESULT ===');
  // Keep alive for up to 120 seconds
  setTimeout(() => {
    console.log('\n=== TIMEOUT ===');
    console.log('Output so far:', output);
    proc.kill();
    process.exit(0);
  }, 120000);
}, 3000);
