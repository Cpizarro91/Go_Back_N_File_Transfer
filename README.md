# Go_Back_N_File_Transfer
Java based client/server for file transfer over UDP
Basis of code derived from MistrQ here on Github, server3 and sender3 files.
I have modified this to take in reliability numbers in which each one simulates different events.

Reliability numbers:
0 - simulates no loss of any packets in window
1 - simulates loss of first packet within the window
2 - simulates loss of all packets within the window
