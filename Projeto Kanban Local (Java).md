# Projeto Kanban Local (Java)

Este projeto implementa um sistema Kanban simples utilizando Java puro e um servidor HTTP embutido. Ele permite a gestão de tarefas em três estados: To-Do, Doing e Done, com persistência de dados em um arquivo CSV.

## Funcionalidades

- **Criação de Tarefas**: Adicione novas tarefas com título e descrição.
- **Gestão de Status**: Mova tarefas entre os estados To-Do, Doing e Done.
- **Exclusão de Tarefas**: Remova tarefas do sistema.
- **Persistência de Dados**: Todas as tarefas são salvas em um arquivo CSV (`data_tasks.csv`) para que os dados não sejam perdidos ao reiniciar o servidor.
- **Interface Web Simples**: A aplicação possui uma interface web minimalista, construída com HTML, CSS e JavaScript puros, acessível via navegador.

## Estrutura do Projeto

O projeto consiste em um único arquivo Java (`App.java`) que contém toda a lógica do servidor HTTP, manipulação de dados e a interface web embutida como uma string.

- `App.java`: Contém a classe principal que inicia o servidor, os manipuladores de requisições HTTP e as funções de manipulação de dados (carregar, salvar, criar, buscar, etc.). A interface HTML/CSS/JavaScript está embutida na constante `INDEX_HTML`.
- `data_tasks.csv`: Arquivo gerado automaticamente para armazenar as tarefas em formato CSV.

## Como Executar

Para executar este projeto, você precisará ter o Java Development Kit (JDK) instalado em sua máquina (versão 17 ou superior é recomendada).

1. **Clone o Repositório (ou baixe o código):**

   ```bash
   git clone https://github.com/gustavobergz/Depuracao-de-codigo.git
   cd Depuracao-de-codigo
   ```

2. **Compile o Código Java:**

   Navegue até o diretório raiz do projeto (`kanban-app` se você seguiu a estrutura proposta) e compile o arquivo `App.java`:

   ```bash
   javac src/main/java/App.java
   ```

   Se você encontrar erros de compilação relacionados a caracteres de escape no `INDEX_HTML`, certifique-se de que o conteúdo HTML esteja corretamente escapado para uma string Java.

3. **Execute a Aplicação:**

   Após a compilação, execute a aplicação:

   ```bash
   java -cp . src/main/java/App.java
   ```

   Você verá uma mensagem no console indicando que o servidor está rodando na porta 8080:

   ```
   Servindo em http://localhost:8080
   ```

4. **Acesse a Aplicação no Navegador:**

   Abra seu navegador web e acesse a URL: `http://localhost:8080`

   Você verá a interface do Kanban, onde poderá adicionar, mover e excluir tarefas.

## Tecnologias Utilizadas

- **Java**: Linguagem de programação principal.
- **HTTP Server (com.sun.net.httpserver)**: Servidor HTTP embutido do Java para lidar com as requisições web.
- **HTML5**: Estrutura da interface web.
- **CSS3**: Estilização da interface web.
- **JavaScript**: Lógica de frontend para interagir com a API do backend.
- **CSV**: Formato de arquivo para persistência de dados.

## Considerações de Design

Este projeto foi desenvolvido com foco na simplicidade e na demonstração de um sistema Kanban funcional sem a necessidade de frameworks complexos. A escolha de Java puro para o backend e HTML/CSS/JavaScript puros para o frontend visa manter a base de código leve e fácil de entender. A persistência em CSV é uma solução simples para armazenamento local de dados.

## Contribuição

Contribuições são bem-vindas! Sinta-se à vontade para abrir issues ou pull requests para melhorias, correções de bugs ou novas funcionalidades.

## Licença

Este projeto está licenciado sob a licença MIT. Veja o arquivo `LICENSE` para mais detalhes. (Nota: O arquivo LICENSE não está incluído neste repositório, mas é uma boa prática incluí-lo em projetos de código aberto.)

---

**Autor**: Manus AI
**Data**: 20 de Agosto de 2025


