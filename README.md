# [BLEMeshChat Android](https://github.com/OnlyInAmerica/BLEMeshChat) [![Build Status](https://travis-ci.org/OnlyInAmerica/BLEMeshChat.svg?branch=master)](https://travis-ci.org/OnlyInAmerica/BLEMeshChat)

[![Screenshot](http://i.imgur.com/GMtn5ol.png)](http://i.imgur.com/GMtn5ol.png)

**Under Development : Not yet ready for use!**

An experiment in pleasant decentralized messaging that works across iOS and Android. This experiment requires Android 5.0 and a device capable of operation as both a Bluetooth LE peripheral and central.

Also see the [iOS client](https://github.com/chrisballinger/BLEMeshChat) and [Protocol Spec](https://github.com/chrisballinger/BLEMeshChat/wiki).

## Motivation

A system for propagating messages directly from device to device is critical in situations where Internet is unavailable. More abstractly, it may also serve as an interesting model for receiving information influenced by the company you keep.

Imagine:

Broadcasting the locations of potable water in a disaster scenario without Internet.

Seeking insulin at a crowded festival where cell service is unreliable.

Organizing movements of a large protest where cellular Internet is jammed.

## Goals

+ This system must be able to operate constantly in the background without a significant effect on battery life.
+ Connections to peers must be made without user intervention.
+ Messages must be signed, and the system must allow the user to verify other users' association with a particular public key.

## License

MPL 2.0