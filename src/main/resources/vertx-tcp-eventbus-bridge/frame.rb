require 'vertx/write_stream'
require 'vertx/multi_map'
require 'vertx/util/utils.rb'
# Generated from io.vertx.ext.eventbus.bridge.tcp.Frame
module VertxTcpEventbusBridge
  #  TCP EventBus bridge Protocol Data Unit (Frame).
  class Frame
    # @private
    # @param j_del [::VertxTcpEventbusBridge::Frame] the java delegate
    def initialize(j_del)
      @j_del = j_del
    end
    # @private
    # @return [::VertxTcpEventbusBridge::Frame] the underlying java delegate
    def j_del
      @j_del
    end
    #  Create a new Frame for a given action.
    # @param [:OK,:ERROR,:MESSAGE,:PUBLISH,:REGISTER,:UNREGISTER] action one of the enum possibilities.
    # @return [::VertxTcpEventbusBridge::Frame] A bare minimal frame.
    def self.create(action=nil)
      if action.class == Symbol && !block_given?
        return ::Vertx::Util::Utils.safe_create(Java::IoVertxExtEventbusBridgeTcp::Frame.java_method(:create, [Java::IoVertxExtEventbusBridgeTcp::Action.java_class]).call(Java::IoVertxExtEventbusBridgeTcp::Action.valueOf(action)),::VertxTcpEventbusBridge::Frame)
      end
      raise ArgumentError, "Invalid arguments when calling create(action)"
    end
    #  Get the action associated with this frame.
    # @return [:OK,:ERROR,:MESSAGE,:PUBLISH,:REGISTER,:UNREGISTER] the action
    def action
      if !block_given?
        return @j_del.java_method(:action, []).call().name.intern
      end
      raise ArgumentError, "Invalid arguments when calling action()"
    end
    #  Get the headers for this frame. The headers are a multimap so they behave just like with HTTP.
    # @return [::Vertx::MultiMap] the headers
    def headers
      if !block_given?
        return ::Vertx::Util::Utils.safe_create(@j_del.java_method(:headers, []).call(),::Vertx::MultiMap)
      end
      raise ArgumentError, "Invalid arguments when calling headers()"
    end
    #  Set the body for this message, internally it will know how to handle several types:
    #  * JsonObject
    #  * String
    #  * Buffer
    # 
    #  Any other type will raise an exception.
    # @param [Object] body body value.
    # @return [self]
    def set_body(body=nil)
      if (body.class == String  || body.class == Hash || body.class == Array || body.class == NilClass || body.class == TrueClass || body.class == FalseClass || body.class == Fixnum || body.class == Float) && !block_given?
        @j_del.java_method(:setBody, [Java::java.lang.Object.java_class]).call(::Vertx::Util::Utils.to_object(body))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling set_body(body)"
    end
    #  Add a header to the frame. It uses the same semantic as with HTTP.
    # @param [String] key header name
    # @param [String] value header value
    # @return [self]
    def add_header(key=nil,value=nil)
      if key.class == String && value.class == String && !block_given?
        @j_del.java_method(:addHeader, [Java::java.lang.String.java_class,Java::java.lang.String.java_class]).call(key,value)
        return self
      end
      raise ArgumentError, "Invalid arguments when calling add_header(key,value)"
    end
    #  Return the associated body, it will perform some conversion based on the `Content-Type` header, specifically for:
    #  * application/json
    #  * test/plain
    # @return [Object] the body
    def get_body
      if !block_given?
        return ::Vertx::Util::Utils.from_object(@j_del.java_method(:getBody, []).call())
      end
      raise ArgumentError, "Invalid arguments when calling get_body()"
    end
    #  Tries to return the body as JSON ignoring if the `Content-Type` header is set or not.
    # @return [Hash{String => Object}] json
    def to_json
      if !block_given?
        return @j_del.java_method(:toJSON, []).call() != nil ? JSON.parse(@j_del.java_method(:toJSON, []).call().encode) : nil
      end
      raise ArgumentError, "Invalid arguments when calling to_json()"
    end
    #  Write this frame into a WriteStream.
    # @param [::Vertx::WriteStream] stream stream such as a socket.
    # @return [void]
    def write(stream=nil)
      if stream.class.method_defined?(:j_del) && !block_given?
        return @j_del.java_method(:write, [Java::IoVertxCoreStreams::WriteStream.java_class]).call(stream.j_del)
      end
      raise ArgumentError, "Invalid arguments when calling write(stream)"
    end
  end
end
