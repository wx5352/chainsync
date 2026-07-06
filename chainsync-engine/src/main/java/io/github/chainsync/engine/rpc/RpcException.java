package io.github.chainsync.engine.rpc;

/** Unchecked wrapper for RPC failures that survive the pool's retry/failover budget. */
public class RpcException extends RuntimeException {

    public RpcException(String message) {
        super(message);
    }

    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }
}
