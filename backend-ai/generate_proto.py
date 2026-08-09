"""
从 .proto 文件生成 Python gRPC 代码。

修改 proto 后运行：
    python generate_proto.py
"""

import subprocess
import sys
import os


def generate_proto():
    """生成 Python protobuf 文件。"""
    proto_dir = os.path.join(os.path.dirname(__file__), 'protos')
    output_dir = os.path.dirname(__file__)
    
    proto_file = os.path.join(proto_dir, 'longcat_chat.proto')
    
    if not os.path.exists(proto_file):
        print(f"Error: Proto file not found: {proto_file}")
        sys.exit(1)
    
    cmd = [
        sys.executable, '-m', 'grpc_tools.protoc',
        f'-I{proto_dir}',
        f'--python_out={output_dir}',
        f'--grpc_python_out={output_dir}',
        proto_file
    ]
    
    print(f"Running: {' '.join(cmd)}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    
    if result.returncode != 0:
        print(f"Error generating proto files:")
        print(result.stderr)
        sys.exit(1)
    
    print("Proto files generated successfully!")
    print(f"  - longcat_chat_pb2.py")
    print(f"  - longcat_chat_pb2_grpc.py")


if __name__ == "__main__":
    generate_proto()
