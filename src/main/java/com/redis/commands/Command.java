package com.redis.commands;

import com.redis.resp.RespToken;

import java.util.List;

/**
 * Common functional interface for all Redis command implementations.
 */
@FunctionalInterface
public interface Command {
    RespToken execute(CommandContext ctx, List<byte[]> args);
}
