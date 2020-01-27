class StRingBuffer
{
  public:
    StRingBuffer(int len);
    String addChar(char val);
    String Value();
    void clear();
    int length();
  private:
    int iLength = 0;
    int pos = 0;
    String data = "";
};

StRingBuffer::StRingBuffer(int len)
{
  iLength = len;
  clear();
}

void StRingBuffer::clear()
{
  data = "";
  for (pos = 0; pos < iLength; pos++)
    data += " ";
  pos = 0;
}

String StRingBuffer::addChar(char val)
{
  if (!isPrintable(val))
    val = ' ';
  data.setCharAt(pos, val);
  pos = (++pos)%iLength;
  return Value();
}

String StRingBuffer::Value()
{
  if (pos > 0 && iLength > 0)
    return data.substring(pos) + data.substring(0, pos);
  else
    return data;
}

int StRingBuffer::length()
{
  return iLength;
}
