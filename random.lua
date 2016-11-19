-- https://github.com/bungle/lua-resty-random


-- Copyright (c) 2013, Aapo Talvensaari
-- All rights reserved.

-- Redistribution and use in source and binary forms, with or without modification,
-- are permitted provided that the following conditions are met:

-- * Redistributions of source code must retain the above copyright notice, this
--   list of conditions and the following disclaimer.

-- * Redistributions in binary form must reproduce the above copyright notice, this
--   list of conditions and the following disclaimer in the documentation and/or
--   other materials provided with the distribution.

-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
-- ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
-- WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
-- DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
-- ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
-- (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
-- LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
-- ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
-- (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
-- SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

local require    = require
local ffi        = require "ffi"
local ffi_cdef   = ffi.cdef
local ffi_new    = ffi.new
local ffi_str    = ffi.string
local ffi_typeof = ffi.typeof
local C          = ffi.C
local type       = type
local random     = math.random
local randomseed = math.randomseed
local concat     = table.concat
local tostring   = tostring
local pcall      = pcall

ffi_cdef[[
typedef unsigned char u_char;
u_char * ngx_hex_dump(u_char *dst, const u_char *src, size_t len);
int RAND_bytes(u_char *buf, int num);
]]

local ok, new_tab = pcall(require, "table.new")
if not ok then
    new_tab = function () return {} end
end

local alnum  = {
    'A','B','C','D','E','F','G','H','I','J','K','L','M',
    'N','O','P','Q','R','S','T','U','V','W','X','Y','Z',
    'a','b','c','d','e','f','g','h','i','j','k','l','m',
    'n','o','p','q','r','s','t','u','v','w','x','y','z',
    '0','1','2','3','4','5','6','7','8','9'
}

local t = ffi_typeof "uint8_t[?]"

local function bytes(len, format)
    local s = ffi_new(t, len)
    C.RAND_bytes(s, len)
    if not s then return nil end
    if format == "hex" then
        local b = ffi_new(t, len * 2)
        C.ngx_hex_dump(b, s, len)
        return ffi_str(b, len * 2), true
    else
        return ffi_str(s, len), true
    end
end

local function seed()
    local a,b,c,d = bytes(4):byte(1, 4)
    return randomseed(a * 0x1000000 + b * 0x10000 + c * 0x100 + d)
end

local function number(min, max, reseed)
    if reseed then seed() end
    if min and max then return random(min, max)
    elseif min     then return random(min)
    else                return random() end
end

local function token(len, chars, sep)
    chars = chars or alnum
    local count
    local token = new_tab(len, 0)
    if type(chars) ~= "table" then
        chars = tostring(chars)
        count = #chars
        local n
        for i=1,len do
            n = number(1, count)
            token[i] = chars:sub(n, n)
        end
    else
        count = #chars
        for i=1,len do token[i] = chars[number(1, count)] end
    end
    return concat(token, sep)
end

seed()

return {
    bytes  = bytes,
    number = number,
    token  = token
}
