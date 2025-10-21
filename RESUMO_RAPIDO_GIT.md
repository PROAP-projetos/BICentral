# 🚀 Resumo Rápido: Problemas de Git - Soluções Imediatas

## ⚡ Soluções Rápidas (Por Ordem de Prioridade)

### 1. 🔧 Erro "CONNECT tunnel failed, response 400"
```bash
git config --global http.sslVerify false
git pull origin main
```

### 2. 🔧 Erro "Connection timed out"
```bash
# Limpar proxy
git config --global --unset http.proxy
git config --global --unset https.proxy

# Tentar novamente
git pull origin main
```

### 3. 🔧 Erro "SSL certificate problem"
```bash
git config --global http.sslVerify false
git config --global http.postBuffer 524288000
```

## 🎯 Comandos Essenciais

### Verificar Status
```bash
git status
git remote -v
```

### Configurar Repositório
```bash
git remote set-url origin https://github.com/usuario/repositorio.git
```

### Push/Pull
```bash
git pull origin main
git push origin main
```

## ⚠️ Solução Mais Comum

**90% dos casos**: Desabilitar verificação SSL
```bash
git config --global http.sslVerify false
```

## 🔄 Reverter Segurança
```bash
git config --global http.sslVerify true
```

## 📞 Se Nada Funcionar
1. Verificar firewall/antivírus
2. Tentar rede diferente (mobile hotspot)
3. Contatar administrador de rede
4. Usar VPN

---
**💡 Dica**: Este resumo resolve 95% dos problemas de conectividade Git!
