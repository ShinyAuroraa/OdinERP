package br.com.odin.crm.web.rest;

import java.util.List;

public record MeResponse(String sub, String name, List<String> roles) {}
