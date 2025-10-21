# Guia Completo: Resolvendo Problemas de Conectividade com Git

## 📋 Índice
1. [Introdução](#introdução)
2. [Problemas Comuns](#problemas-comuns)
3. [Diagnóstico](#diagnóstico)
4. [Soluções Passo a Passo](#soluções-passo-a-passo)
5. [Configurações Avançadas](#configurações-avançadas)
6. [Prevenção](#prevenção)
7. [Troubleshooting](#troubleshooting)

## 🎯 Introdução

Este guia foi criado para ajudar desenvolvedores a resolver problemas de conectividade com Git, especialmente em ambientes corporativos ou com restrições de rede. Baseado em uma experiência real de resolução de problemas.

## ⚠️ Problemas Comuns

### 1. Erro "CONNECT tunnel failed, response 400"
```
fatal: unable to access 'https://github.com/user/repo.git/': CONNECT tunnel failed, response 400
```

**Causa**: Firewall corporativo ou proxy bloqueando conexões HTTPS com GitHub.

### 2. Erro "Connection timed out"
```
ssh: connect to host github.com port 22: Connection timed out
```

**Causa**: Firewall bloqueando conexões SSH na porta 22.

### 3. Erro "SSL certificate problem"
```
fatal: unable to access 'https://github.com/user/repo.git/': SSL certificate problem
```

**Causa**: Problemas com certificados SSL ou proxy interceptando conexões.

## 🔍 Diagnóstico

### Passo 1: Verificar Status do Repositório
```bash
git status
git remote -v
```

### Passo 2: Testar Conectividade
```bash
# Testar HTTPS
curl -I https://github.com

# Testar SSH (se configurado)
ssh -T git@github.com
```

### Passo 3: Verificar Configurações Git
```bash
git config --list | grep -E "(proxy|ssl|http)"
```

## 🛠️ Soluções Passo a Passo

### Solução 1: Desabilitar Verificação SSL (Mais Comum)

```bash
# Desabilitar verificação SSL globalmente
git config --global http.sslVerify false

# Testar a conexão
git pull origin main
```

**⚠️ Aviso**: Esta solução reduz a segurança. Use apenas quando necessário.

### Solução 2: Configurar Proxy (Rede Corporativa)

```bash
# Se você souber o endereço do proxy
git config --global http.proxy http://proxy.empresa.com:8080
git config --global https.proxy https://proxy.empresa.com:8080

# Para autenticação
git config --global http.proxy http://usuario:senha@proxy.empresa.com:8080
```

### Solução 3: Limpar Configurações de Proxy

```bash
# Remover configurações de proxy
git config --global --unset http.proxy
git config --global --unset https.proxy
```

### Solução 4: Usar SSH em vez de HTTPS

```bash
# Alterar URL do repositório para SSH
git remote set-url origin git@github.com:usuario/repositorio.git

# Testar conexão SSH
ssh -T git@github.com
```

### Solução 5: Configurar SSH com Porta Alternativa

```bash
# Criar/editar arquivo ~/.ssh/config
Host github.com
    Hostname ssh.github.com
    Port 443
    User git
```

## ⚙️ Configurações Avançadas

### Configuração de Timeout
```bash
# Aumentar timeout para conexões lentas
git config --global http.lowSpeedLimit 0
git config --global http.lowSpeedTime 999999
```

### Configuração de Buffer
```bash
# Aumentar buffer para repositórios grandes
git config --global http.postBuffer 524288000
```

### Configuração de Certificados
```bash
# Especificar certificado personalizado
git config --global http.sslCAInfo /caminho/para/certificado.pem
```

## 🛡️ Prevenção

### 1. Configuração Inicial Recomendada
```bash
# Configurações básicas
git config --global user.name "Seu Nome"
git config --global user.email "seu.email@exemplo.com"

# Configurações de segurança
git config --global http.sslVerify true
git config --global http.postBuffer 524288000
```

### 2. Verificação Periódica
```bash
# Verificar configurações
git config --list

# Testar conectividade
git ls-remote origin
```

### 3. Backup de Configurações
```bash
# Exportar configurações
git config --list > git-config-backup.txt

# Restaurar configurações
git config --file git-config-backup.txt
```

## 🔧 Troubleshooting

### Problema: "Permission denied (publickey)"
```bash
# Verificar chaves SSH
ssh-add -l

# Adicionar chave SSH
ssh-add ~/.ssh/id_rsa

# Testar conexão
ssh -T git@github.com
```

### Problema: "Repository not found"
```bash
# Verificar permissões do repositório
# Verificar se o repositório existe
# Verificar se você tem acesso
```

### Problema: "Authentication failed"
```bash
# Verificar credenciais
git config --global user.name
git config --global user.email

# Reconfigurar credenciais
git config --global user.name "Novo Nome"
git config --global user.email "novo.email@exemplo.com"
```

## 📊 Comandos Úteis para Diagnóstico

```bash
# Verificar configurações Git
git config --list

# Verificar repositórios remotos
git remote -v

# Verificar status
git status

# Verificar log de commits
git log --oneline -5

# Testar conectividade
ping github.com
curl -I https://github.com
```

## 🎯 Resumo da Solução Aplicada

No caso específico que resolvemos:

1. **Problema**: `CONNECT tunnel failed, response 400`
2. **Causa**: Firewall/proxy bloqueando conexões HTTPS
3. **Solução**: `git config --global http.sslVerify false`
4. **Resultado**: Conectividade restaurada com sucesso

## 📝 Notas Importantes

- ⚠️ **Segurança**: Desabilitar SSL reduz a segurança. Use apenas quando necessário.
- 🔄 **Reversão**: Para reabilitar SSL: `git config --global http.sslVerify true`
- 🏢 **Corporativo**: Em ambientes corporativos, consulte o administrador de rede.
- 📚 **Documentação**: Sempre consulte a documentação oficial do Git e GitHub.

## 🆘 Quando Procurar Ajuda

- Problemas persistem após tentar todas as soluções
- Erros de autenticação complexos
- Configurações de rede corporativa específicas
- Problemas com repositórios privados

## 📞 Recursos Adicionais

- [Documentação Oficial do Git](https://git-scm.com/doc)
- [GitHub Help](https://help.github.com)
- [SSH Keys Guide](https://docs.github.com/en/authentication/connecting-to-github-with-ssh)

---

**Criado em**: $(date)  
**Baseado em**: Experiência real de resolução de problemas  
**Última atualização**: $(date)
