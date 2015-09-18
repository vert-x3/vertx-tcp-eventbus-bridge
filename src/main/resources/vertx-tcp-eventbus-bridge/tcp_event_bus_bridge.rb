require 'vertx/vertx'
require 'vertx/util/utils.rb'
# Generated from io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge
module VertxTcpEventbusBridge
  #  TCP EventBus bridge for Vert.x
  class TcpEventBusBridge
    # @private
    # @param j_del [::VertxTcpEventbusBridge::TcpEventBusBridge] the java delegate
    def initialize(j_del)
      @j_del = j_del
    end
    # @private
    # @return [::VertxTcpEventbusBridge::TcpEventBusBridge] the underlying java delegate
    def j_del
      @j_del
    end
    # @param [::Vertx::Vertx] vertx 
    # @param [Hash] options 
    # @return [::VertxTcpEventbusBridge::TcpEventBusBridge]
    def self.create(vertx=nil,options=nil)
      if vertx.class.method_defined?(:j_del) && !block_given? && options == nil
        return ::Vertx::Util::Utils.safe_create(Java::IoVertxExtEventbusBridgeTcp::TcpEventBusBridge.java_method(:create, [Java::IoVertxCore::Vertx.java_class]).call(vertx.j_del),::VertxTcpEventbusBridge::TcpEventBusBridge)
      elsif vertx.class.method_defined?(:j_del) && options.class == Hash && !block_given?
        return ::Vertx::Util::Utils.safe_create(Java::IoVertxExtEventbusBridgeTcp::TcpEventBusBridge.java_method(:create, [Java::IoVertxCore::Vertx.java_class,Java::IoVertxCoreNet::NetServerOptions.java_class]).call(vertx.j_del,Java::IoVertxCoreNet::NetServerOptions.new(::Vertx::Util::Utils.to_json_object(options))),::VertxTcpEventbusBridge::TcpEventBusBridge)
      end
      raise ArgumentError, "Invalid arguments when calling create(vertx,options)"
    end
    # @param [Hash] permitted 
    # @return [self]
    def add_inbound_permitted(permitted=nil)
      if permitted.class == Hash && !block_given?
        @j_del.java_method(:addInboundPermitted, [Java::IoVertxExtEventbusBridgeTcp::PermittedOptions.java_class]).call(Java::IoVertxExtEventbusBridgeTcp::PermittedOptions.new(::Vertx::Util::Utils.to_json_object(permitted)))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling add_inbound_permitted(permitted)"
    end
    # @param [Hash] permitted 
    # @return [self]
    def add_outbound_permitted(permitted=nil)
      if permitted.class == Hash && !block_given?
        @j_del.java_method(:addOutboundPermitted, [Java::IoVertxExtEventbusBridgeTcp::PermittedOptions.java_class]).call(Java::IoVertxExtEventbusBridgeTcp::PermittedOptions.new(::Vertx::Util::Utils.to_json_object(permitted)))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling add_outbound_permitted(permitted)"
    end
    # @param [Fixnum] port 
    # @param [String] address 
    # @yield 
    # @return [self]
    def listen(port=nil,address=nil)
      if !block_given? && port == nil && address == nil
        @j_del.java_method(:listen, []).call()
        return self
      elsif block_given? && port == nil && address == nil
        @j_del.java_method(:listen, [Java::IoVertxCore::Handler.java_class]).call((Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ::Vertx::Util::Utils.safe_create(ar.result,::VertxTcpEventbusBridge::TcpEventBusBridge) : nil) }))
        return self
      elsif port.class == Fixnum && !block_given? && address == nil
        @j_del.java_method(:listen, [Java::int.java_class]).call(port)
        return self
      elsif port.class == Fixnum && address.class == String && !block_given?
        @j_del.java_method(:listen, [Java::int.java_class,Java::java.lang.String.java_class]).call(port,address)
        return self
      elsif port.class == Fixnum && block_given? && address == nil
        @j_del.java_method(:listen, [Java::int.java_class,Java::IoVertxCore::Handler.java_class]).call(port,(Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ::Vertx::Util::Utils.safe_create(ar.result,::VertxTcpEventbusBridge::TcpEventBusBridge) : nil) }))
        return self
      elsif port.class == Fixnum && address.class == String && block_given?
        @j_del.java_method(:listen, [Java::int.java_class,Java::java.lang.String.java_class,Java::IoVertxCore::Handler.java_class]).call(port,address,(Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ::Vertx::Util::Utils.safe_create(ar.result,::VertxTcpEventbusBridge::TcpEventBusBridge) : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling listen(port,address)"
    end
    # @yield 
    # @return [void]
    def close
      if !block_given?
        return @j_del.java_method(:close, []).call()
      elsif block_given?
        return @j_del.java_method(:close, [Java::IoVertxCore::Handler.java_class]).call((Proc.new { |ar| yield(ar.failed ? ar.cause : nil) }))
      end
      raise ArgumentError, "Invalid arguments when calling close()"
    end
  end
end
