package exceptions

class SSLNotSupportedException 
  extends Exception("https.port is not defined. Run with 'activator -Dhttps.port=9443 run'")