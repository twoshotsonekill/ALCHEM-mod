#!/usr/bin/env python3
"""aifuzz entry point."""
import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

from aifuzz.cli import main

if __name__ == "__main__":
    main()
