"use strict";

var CACHE="pp-tv-v1";
var FILES=[
 "./",
 "./index.html",
 "./manifest.webmanifest",
 "./icons/icon-192.png",
 "./icons/icon-512.png"
];

self.addEventListener("install",function(e){
 e.waitUntil(
  caches.open(CACHE).then(function(c){
   return c.addAll(FILES);
  })
 );
});

self.addEventListener("activate",function(e){
 e.waitUntil(self.clients.claim());
});

self.addEventListener("fetch",function(e){
 e.respondWith(
  caches.match(e.request).then(function(r){
   return r||fetch(e.request);
  })
 );
});