// List available MCP tools
const { spawn } = require('child_process');

const nodePath = 'C:\\Users\\23911\\AppData\\Roaming\\TRAE SOLO CN\\ModularData\\ai-agent\\vm\\tools\\node\\node.exe';
const makerPath = 'C:\\Users\\23911\\.taptap-maker\\mcp-runtime\\0.0.32\\dist\\maker.js';

const proc = spawn(nodePath, [makerPath], {
  cwd: 'D:\\GameDev',
  env: { ...process.env, TAPTAP_MCP_CLIENT_IDE: 'trae' },
  stdio: ['pipe', 'pipe', 'pipe']
});

proc.stdout.on('data', (data) => console.log(data.toString()));
proc.stderr.on('data', (data) => console.error('STDERR:', data.toString()));

const init = JSON.stringify({
  jsonrpc: '2.0', id: 0, method: 'initialize',
  params: { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'trae', version: '1.0' } }
});
proc.stdin.write(init + '\n');

setTimeout(() => {
  const notif = JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' });
  proc.stdin.write(notif + '\n');

  setTimeout(() => {
    const listTools = JSON.stringify({
      jsonrpc: '2.0', id: 1, method: 'tools/list',
      params: {}
    });
    proc.stdin.write(listTools + '\n');

    setTimeout(() => {
      proc.kill();
      process.exit(0);
    }, 5000);
  }, 1000);
}, 2000);
