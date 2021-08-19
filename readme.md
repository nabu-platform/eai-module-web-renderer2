# Installation

On a debian machine we kept getting the vague error that chrome could not be found.

On an ubuntu server, if you don't have chrome installed, you get a clear error stating you need to fill in the environment parameter CHROME_PATH.
However, just installing it (without adding that parameter) was enough:

```
$ sudo apt-get install chromium-browser
```

After this step, it worked automatically.

It is possible that the debian machine would've worked if we set the parameter, however the error message was not quite as clear.

```
CHROME_PATH = /usr/bin/chromium-browser
```
