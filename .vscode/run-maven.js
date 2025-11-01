const { spawn } = require('child_process');
const mvn = spawn('mvn', ['javafx:run'], { stdio: 'inherit', shell: true });
mvn.on('exit', code => process.exit(code));
