import { FrotasService } from './frotas.service';

describe('FrotasService - alertas de manutenção', () => {
  let service: FrotasService;
  let fetchMock: jest.Mock;

  beforeEach(() => {
    service = new FrotasService();
    fetchMock = jest.fn();
    globalThis.fetch = fetchMock as unknown as typeof fetch;
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('getAlertas sem status chama a URL sem query string', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => [{ id: 1 }] });

    const result = await service.getAlertas();

    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/alertas-manutencao');
    expect(result).toEqual([{ id: 1 }]);
  });

  it('getAlertas com status envia ?status= codificado', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => [] });

    await service.getAlertas('FALHA');

    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/alertas-manutencao?status=FALHA');
  });

  it('getAlertas usa encodeURIComponent no status', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => [] });

    await service.getAlertas('A&B');

    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/alertas-manutencao?status=A%26B');
  });

  it('getAlertas retorna array vazio quando a resposta não é ok', async () => {
    fetchMock.mockResolvedValue({ ok: false, json: async () => ({}) });

    expect(await service.getAlertas('XYZ')).toEqual([]);
  });

  it('getAlertas retorna array vazio quando o fetch falha', async () => {
    fetchMock.mockRejectedValue(new Error('rede'));

    expect(await service.getAlertas()).toEqual([]);
  });

  it('verificarAlertas faz POST sem body e retorna o resumo', async () => {
    const resumo = { veiculosVerificados: 7, criados: 2, alertas: [] };
    fetchMock.mockResolvedValue({ ok: true, json: async () => resumo });

    const result = await service.verificarAlertas();

    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/alertas-manutencao/verificar', {
      method: 'POST'
    });
    expect(result).toEqual(resumo);
  });

  it('verificarAlertas retorna null quando a resposta não é ok', async () => {
    fetchMock.mockResolvedValue({ ok: false, json: async () => ({}) });

    expect(await service.verificarAlertas()).toBeNull();
  });

  it('verificarAlertas retorna null quando o fetch falha', async () => {
    fetchMock.mockRejectedValue(new Error('rede'));

    expect(await service.verificarAlertas()).toBeNull();
  });
});
