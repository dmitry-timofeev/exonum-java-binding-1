## Overview
ELF:
 * segments — for a running program;
 * sections — for linking.

rpath & runpath — can allow to specify some search paths for dynamic shared libraries (in our case, that must be /lib).

rpath — before searching LD_LIBRARY_PATH (therefore, cannot be changed dynamically)
runpath — after

Paths can be relative to the executable, not the working directory: $ORIGIN!

https://amir.rachum.com/blog/2016/09/17/shared-libraries/#debugging-cheat-sheet

## How to print offset in a lib:
(need to read man, but here is a snippet:)
```
$ addr2line -j .text -e libfoo.so 0xcafebabe
```

No `.text` in the case of MemSan output (absolute offsets)?

Src: https://stackoverflow.com/questions/7556045/how-to-map-function-address-to-function-in-so-files

## How to print expected shared libraries:
As easy as:
```
$ ldd libfoo.so

$ ldd bar.exe
```
