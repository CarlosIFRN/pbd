# SPEC — PetShop Manager

## 1. Objetivo

Aplicação desktop (executada como SPA web empacotada / acessada localmente) para gerenciamento de
animais atendidos por um pet shop, com:

- Front-end em **Angular**
- Back-end em **API REST .NET** (ASP.NET Core Web API)
- Banco de dados **SQL Server** (ou equivalente relacional)
- Autenticação obrigatória via **JWT** para todas as telas do sistema
- Preenchimento automático de endereço via **API ViaCEP** no cadastro do tutor

Este documento formaliza os requisitos do enunciado da disciplina em uma especificação técnica
implementável: modelo de dados, contrato de API, fluxo de autenticação e telas.

## 2. Escopo

### 2.1 Requisitos funcionais obrigatórios

| # | Requisito |
|---|-----------|
| RF01 | Login de usuário com e-mail/usuário e senha, retornando um JWT |
| RF02 | Todas as telas do sistema (exceto login) exigem JWT válido |
| RF03 | CRUD completo de Animais (criar, listar, editar, excluir) |
| RF04 | Cadastro de Tutor vinculado ao Animal, com endereço |
| RF05 | Consulta automática de CEP (ViaCEP) preenchendo Logradouro, Bairro, Cidade e UF |
| RF06 | Upload/armazenamento de foto do animal |

### 2.2 Fora de escopo (não pedido pelo enunciado)

- Cadastro de usuários pela interface (usuário será pré-criado via seed/script SQL)
- Recuperação de senha, múltiplos perfis/permissões granulares
- Agendamento de serviços, controle financeiro, estoque

## 3. Arquitetura da solução

```
petshop-manager/
├── backend/                 # ASP.NET Core Web API (.NET 8)
│   ├── PetShop.Api/          # Controllers, Program.cs, JWT config
│   ├── PetShop.Domain/       # Entidades (Animal, Tutor, Endereco, Usuario)
│   ├── PetShop.Infrastructure/# EF Core DbContext, Migrations, Repositórios
│   └── PetShop.Api.Tests/
├── frontend/                 # Angular (standalone components)
│   └── src/app/
│       ├── core/auth/         # AuthService, JWT interceptor, auth guard
│       ├── core/cep/          # ViaCepService
│       ├── features/login/
│       ├── features/animais/  # listagem, form (incluir/editar)
│       └── shared/
└── database/
    └── schema.sql             # script de criação + seed do usuário de login
```

Fluxo geral: Angular (SPA) → HttpClient com interceptor JWT → ASP.NET Core Web API → Entity
Framework Core → SQL Server.

## 4. Modelo de dados

O enunciado pede três tabelas relacionadas contendo os campos de Animal, Tutor e Endereço. Para
respeitar normalização (evitar repetição de tutor/endereço a cada animal cadastrado), o modelo é
dividido assim:

- **Tutores** — dados do responsável pelo animal
- **Enderecos** — endereço do tutor (1:1 com Tutor), preenchido via ViaCEP
- **Animais** — dados do animal, com FK para Tutor

Uma quarta tabela, **Usuarios**, é necessária para a autenticação (RF01/RF02), embora não esteja
listada explicitamente no enunciado de campos do Animal.

### 4.1 Diagrama

```
Usuarios (login)                Tutores ──1:1── Enderecos
                                    │
                                    │ 1:N
                                    ▼
                                 Animais
```

### 4.2 Tabelas

**Tutores**
| Coluna | Tipo | Regras |
|---|---|---|
| Id | INT IDENTITY | PK |
| Nome | NVARCHAR(150) | NOT NULL |
| Telefone | NVARCHAR(20) | NULL |

**Enderecos**
| Coluna | Tipo | Regras |
|---|---|---|
| Id | INT IDENTITY | PK |
| TutorId | INT | FK → Tutores.Id, UNIQUE, NOT NULL |
| CEP | CHAR(8) | NOT NULL |
| Logradouro | NVARCHAR(150) | NOT NULL |
| Numero | NVARCHAR(10) | NOT NULL |
| Bairro | NVARCHAR(100) | NOT NULL |
| Cidade | NVARCHAR(100) | NOT NULL |
| UF | CHAR(2) | NOT NULL |

**Animais**
| Coluna | Tipo | Regras |
|---|---|---|
| Id | INT IDENTITY | PK |
| Nome | NVARCHAR(100) | NOT NULL |
| Idade | INT | NULL (pode ser derivada de DataNascimento) |
| Peso | DECIMAL(5,2) | NOT NULL |
| DataNascimento | DATE | NOT NULL |
| Foto | VARBINARY(MAX) ou NVARCHAR(255) | caminho do arquivo ou blob |
| Especie | NVARCHAR(50) | NOT NULL |
| TutorId | INT | FK → Tutores.Id, NOT NULL |

**Usuarios**
| Coluna | Tipo | Regras |
|---|---|---|
| Id | INT IDENTITY | PK |
| NomeUsuario | NVARCHAR(50) | NOT NULL, UNIQUE |
| Email | NVARCHAR(150) | NOT NULL, UNIQUE |
| SenhaHash | NVARCHAR(255) | NOT NULL — hash BCrypt, nunca texto puro |
| Role | NVARCHAR(20) | NOT NULL, DEFAULT 'admin' |

### 4.3 Script SQL de referência (a gerar em `database/schema.sql`)

```sql
CREATE TABLE Tutores (
    Id INT IDENTITY PRIMARY KEY,
    Nome NVARCHAR(150) NOT NULL,
    Telefone NVARCHAR(20) NULL
);

CREATE TABLE Enderecos (
    Id INT IDENTITY PRIMARY KEY,
    TutorId INT NOT NULL UNIQUE REFERENCES Tutores(Id),
    CEP CHAR(8) NOT NULL,
    Logradouro NVARCHAR(150) NOT NULL,
    Numero NVARCHAR(10) NOT NULL,
    Bairro NVARCHAR(100) NOT NULL,
    Cidade NVARCHAR(100) NOT NULL,
    UF CHAR(2) NOT NULL
);

CREATE TABLE Animais (
    Id INT IDENTITY PRIMARY KEY,
    Nome NVARCHAR(100) NOT NULL,
    Idade INT NULL,
    Peso DECIMAL(5,2) NOT NULL,
    DataNascimento DATE NOT NULL,
    Foto NVARCHAR(255) NULL,
    Especie NVARCHAR(50) NOT NULL,
    TutorId INT NOT NULL REFERENCES Tutores(Id)
);

CREATE TABLE Usuarios (
    Id INT IDENTITY PRIMARY KEY,
    NomeUsuario NVARCHAR(50) NOT NULL UNIQUE,
    Email NVARCHAR(150) NOT NULL UNIQUE,
    SenhaHash NVARCHAR(255) NOT NULL,
    Role NVARCHAR(20) NOT NULL DEFAULT 'admin'
);

-- Seed do usuário de login (ver seção 6) — SenhaHash gerado com BCrypt na implementação
INSERT INTO Usuarios (NomeUsuario, Email, SenhaHash, Role)
VALUES ('admin', 'admin@petshop.com', '<hash-bcrypt-de-Admin@123>', 'admin');
```

## 5. Autenticação JWT

### 5.1 Fluxo

1. Angular envia `POST /api/auth/login` com `{ usuario, senha }`.
2. API busca o `Usuario` pelo `NomeUsuario` ou `Email`, valida a senha com `BCrypt.Verify`.
3. Se válido, API gera um JWT assinado (HMAC-SHA256) contendo claims:
   - `sub` (Id do usuário), `name` (NomeUsuario), `role`, `exp` (expiração, ex. 2h), `iat`.
4. API retorna `{ token, expiresAt, usuario: { nomeUsuario, role } }`.
5. Angular guarda o token (em memória + `sessionStorage`) via `AuthService`.
6. Um `HttpInterceptor` anexa `Authorization: Bearer <token>` em toda requisição HTTP subsequente.
7. Um `authGuard` (route guard funcional) bloqueia navegação para rotas protegidas se não houver
   token válido, redirecionando para `/login`.
8. No back-end, `[Authorize]` em todos os controllers de negócio (Animais, Tutores) exige o JWT;
   apenas `AuthController.Login` é `[AllowAnonymous]`.
9. Ao receber `401` em qualquer resposta, o interceptor limpa o token e redireciona para `/login`.

### 5.2 Configuração do token (appsettings.json)

```json
{
  "Jwt": {
    "Issuer": "PetShopManager",
    "Audience": "PetShopManagerClient",
    "SecretKey": "<chave-secreta-forte-min-32-chars>",
    "ExpiresInMinutes": 120
  }
}
```

> A `SecretKey` deve vir de variável de ambiente / user-secrets em produção, nunca commitada em
> texto puro no repositório.

### 5.3 Endpoints de autenticação

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| POST | `/api/auth/login` | Não | Recebe usuário/senha, retorna JWT |
| GET | `/api/auth/me` | Sim | Retorna dados do usuário autenticado (para restaurar sessão) |

## 6. Usuário de login (seed)

Como o cadastro de usuários está fora de escopo, o sistema deve nascer com um usuário fixo,
inserido via script SQL/migration, para uso na apresentação:

| Campo | Valor |
|---|---|
| Usuário | `admin` |
| E-mail | `admin@petshop.com` |
| Senha | `Admin@123` |
| Role | `admin` |

Regras de implementação:
- A senha nunca é armazenada em texto puro — a implementação deve gerar o hash BCrypt de
  `Admin@123` e inserir esse hash na coluna `SenhaHash` (seed via `DbContext.OnModelCreating`
  `HasData` ou script SQL pós-geração do hash).
- Este usuário/senha deve ser informado na documentação de entrega para o professor testar o
  login.

## 7. Contrato da API — Animais

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| GET | `/api/animais` | Sim | Lista todos os animais (com dados do tutor) |
| GET | `/api/animais/{id}` | Sim | Detalhe de um animal |
| POST | `/api/animais` | Sim | Cria animal + tutor + endereço |
| PUT | `/api/animais/{id}` | Sim | Atualiza animal/tutor/endereço |
| DELETE | `/api/animais/{id}` | Sim | Remove animal |

### 7.1 Payload de criação/edição (exemplo)

```json
{
  "nome": "Rex",
  "idade": 3,
  "peso": 12.5,
  "dataNascimento": "2023-01-15",
  "especie": "Cachorro",
  "foto": "base64-ou-caminho",
  "tutor": {
    "nome": "Maria Silva",
    "telefone": "84999998888",
    "endereco": {
      "cep": "59000000",
      "logradouro": "Rua das Flores",
      "numero": "123",
      "bairro": "Centro",
      "cidade": "Natal",
      "uf": "RN"
    }
  }
}
```

## 8. Integração ViaCEP (front-end)

- Serviço Angular `ViaCepService.consultar(cep: string)` chama
  `GET https://viacep.com.br/ws/{cep}/json/` diretamente do navegador (sem passar pelo back-end).
- Disparado no formulário de tutor ao sair do campo CEP (`blur`) ou ao completar 8 dígitos.
- Em caso de sucesso, preenche automaticamente `Logradouro`, `Bairro`, `Cidade` e `UF`
  (campo `uf` do retorno) e libera o campo `Numero` para digitação manual.
- Em caso de CEP inválido (`{"erro": true}` ou erro HTTP), exibe mensagem de erro e mantém os
  campos editáveis manualmente.

## 9. Telas (Angular)

| Tela | Rota | Protegida por JWT | Descrição |
|---|---|---|---|
| Login | `/login` | Não | Formulário usuário/senha |
| Listagem de Animais | `/animais` | Sim | Tabela com busca, editar, excluir, botão "Novo" |
| Novo Animal | `/animais/novo` | Sim | Formulário de cadastro (animal + tutor + endereço com ViaCEP) |
| Editar Animal | `/animais/:id/editar` | Sim | Mesmo formulário, pré-preenchido |

Navegação protegida via `authGuard` nas rotas `animais/**`; layout autenticado exibe usuário
logado e botão de logout (que limpa o token e redireciona para `/login`).

## 10. Critérios de aceite

- [ ] Login funcional com o usuário seed retorna JWT válido
- [ ] Acessar `/animais` sem token redireciona para `/login`
- [ ] CRUD de Animais completo e persistindo em SQL Server
- [ ] CEP válido preenche Logradouro/Bairro/Cidade/UF automaticamente
- [ ] Script SQL (`database/schema.sql`) cria as 4 tabelas e insere o usuário seed
- [ ] Todos os integrantes do grupo conseguem explicar o código produzido

## 11. Próximos passos (implementação)

1. Gerar solução .NET (`PetShop.Api`) com EF Core, `AuthController`, `AnimaisController`, JWT
   middleware.
2. Gerar projeto Angular com roteamento, `AuthService`, interceptor, guard, `ViaCepService`.
3. Escrever `database/schema.sql` com as tabelas da seção 4 e o INSERT do usuário seed com hash
   real gerado por BCrypt.
4. Testar fluxo completo: login → listar → incluir (com CEP) → editar → excluir.
