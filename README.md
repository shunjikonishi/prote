# PROTE

A reverse proxy server for testing.

## Features
- Realtime request monitor
- Pretty print for json body
- Decode x-www-urlencoded body
- Generate test for mocha
- WebSocket support

## Install
```
git clone https://github.com/shunjikonishi/prote.git
```

## Run

```
export TARGET_HOST=<YOUR APP HOST>
./activator -Dhttps.port=9443 -Dhttp.port=9000 run
```

activator command is installed in root directory.  
So you can run this app with out install anything.

TARGET_HOST is host name of testing server.  
If your app requires https, you must specify https.port in run command.

## Usage
At first, you should access http://localhost:9000/CONSOLE/main with the browser.  
Next, access http://localhost:9000 with same browser.

All requests from the same browser are shown in CONSOLE.

